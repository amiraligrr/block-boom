"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import {
  GameEngine,
  UPGRADE_DEFS,
  upgradeCost,
  formatNumber,
  loadSave,
  persistSave,
  refreshHearts,
} from "../../lib/game";
import {
  getDeviceId,
  openRecordRun,
  submitRecordScore,
} from "../../lib/api";
import { FALLBACK_SNAP, TXT } from "./play-i18n";

const PlayContext = createContext(null);

export function usePlay() {
  const context = useContext(PlayContext);
  if (!context) {
    throw new Error("usePlay must be used inside <PlayProvider>.");
  }
  return context;
}

export function PlayProvider({ children }) {
  const router = useRouter();
  const pathname = usePathname();
  const engineRef = useRef(null);
  const [engine, setEngine] = useState(null);
  const [snap, setSnap] = useState(FALLBACK_SNAP);
  const [lang, setLang] = useState("fa");
  const [name, setNameState] = useState("");
  const [toast, setToast] = useState("");
  const [completeInfo, setCompleteInfo] = useState(null);
  const [overInfo, setOverInfo] = useState(null);
  const [submitInfo, setSubmitInfo] = useState(null);
  const [installPrompt, setInstallPrompt] = useState(null);
  const [standalone, setStandalone] = useState(false);
  const [hasMoved, setHasMoved] = useState(false);
  const [runs, setRuns] = useState([]);
  const snapRef = useRef(snap);
  const toastTimer = useRef(null);

  /**
   * One record attempt's server session.
   *
   * A run token is opened once, when the attempt starts, and stays open for
   * the whole run. Opening a fresh token at every level would leave the
   * previous one unconsumed and the server would reject the next start; keeping
   * one token also gives the server the real play time for the whole attempt,
   * which is what makes the level/score plausibility check fair.
   */
  const runSessionRef = useRef(null);

  snapRef.current = snap;
  const t = TXT[lang] || TXT.fa;
  const tRef = useRef(t);
  tRef.current = t;
  const langRef = useRef(lang);
  langRef.current = lang;

  const showToast = (message) => {
    setToast(message);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(""), 2600);
  };

  const beginRunSession = (mode) => {
    const session = {
      mode,
      token: "",
      startedAt: Date.now(),
      events: 0,
      chain: "",
      submitted: false,
    };
    runSessionRef.current = session;
    setSubmitInfo(null);
    if (mode !== "record") return;

    const deviceId = getDeviceId();
    if (!deviceId) return;
    openRecordRun(deviceId)
      .then((token) => {
        if (runSessionRef.current === session && token) session.token = token;
      })
      .catch(() => {
        // The server accepts a tokenless submission as a fallback. The run is
        // never blocked on the network.
      });
  };

  const submitRunRecord = async (info) => {
    const current = engineRef.current;
    const session = runSessionRef.current;
    if (!current || !session || session.mode !== "record" || session.submitted) return;

    const score = Number(info?.runScore ?? info?.score ?? current.runScore) || 0;
    if (score <= 0) return;

    const level = Number(info?.level ?? current.level) || 1;
    const playerName =
      (snapRef.current.name || "").trim() ||
      tRef.current.guestName ||
      "Player";
    const deviceId = getDeviceId();
    if (!deviceId) {
      setSubmitInfo({ status: "failed", message: tRef.current.recordFailed });
      return;
    }

    session.submitted = true;
    setSubmitInfo({ status: "submitting" });

    const result = await submitRecordScore({
      name: playerName,
      level,
      score,
      deviceId,
      token: session.token,
      events: Number(info?.runBlocks ?? current.runBlocks ?? 0) || 0,
      chain: session.chain,
      durationMs: Math.max(0, Date.now() - session.startedAt),
    });

    if (result.ok) {
      const rank = Number(result.data?.rank) || 0;
      const message =
        result.message === "No need to update"
          ? tRef.current.recordNotImproved
          : rank > 0
            ? `${tRef.current.recordSaved} — ${tRef.current.rank} ${formatNumber(rank, langRef.current)}`
            : tRef.current.recordSaved;
      setSubmitInfo({ status: "done", rank, message });
      return;
    }

    // Let the player retry the exact same run. The server releases a token
    // when it refuses a score, and a network failure never consumed it.
    session.submitted = false;
    const message =
      result.status === 0
        ? tRef.current.leaderboardError
        : result.error || tRef.current.recordFailed;
    setSubmitInfo({ status: "failed", message });
  };

  const retrySubmit = () => {
    const session = runSessionRef.current;
    if (!session || session.mode !== "record") return;
    session.submitted = false;
    void submitRunRecord(overInfo || completeInfo || snapRef.current);
  };

  useEffect(() => {
    const saved = loadSave();
    refreshHearts(saved);
    persistSave(saved);

    const canvas = document.createElement("canvas");
    const nextEngine = new GameEngine(canvas, {
      save: saved,
      lang: "fa",
      onState: () => setSnap(nextEngine.snapshot()),
      onSave: (next) => persistSave(next),
      onEvent: (event) => {
        const current = nextEngine;

        if (event.type === "levelComplete") {
          setCompleteInfo(event);
          setRuns((previous) =>
            [
              ...previous,
              {
                type: "complete",
                mode: event.mode || nextEngine.mode,
                level: event.level,
                score: event.score,
                runScore: event.runScore,
                runCoins: event.runCoins,
                coins: event.coins,
                best: event.best,
                at: Date.now(),
              },
            ].slice(-20),
          );

          if ((event.mode || nextEngine.mode) === "record") {
            // Record mode has no "next level" button and no upgrade stop. The
            // board ranks score, so the run keeps moving: a short breather,
            // then the next harder wave starts by itself.
            const reward = [];
            if (event.chest?.coins) reward.push(`🎁 +${formatNumber(event.chest.coins, langRef.current)}`);
            if (event.perfectBonus > 0) reward.push(`✨ +${formatNumber(event.perfectBonus, langRef.current)}`);
            showToast(
              `${tRef.current.wave} ${formatNumber(event.level + 1, langRef.current)}${
                reward.length ? ` · ${reward.join(" · ")}` : ""
              }`,
            );
            window.setTimeout(() => {
              if (engineRef.current !== nextEngine) return;
              if (nextEngine.mode !== "record" || nextEngine.status !== "complete") return;
              nextEngine.nextLevel();
            }, 900);
            return;
          }

          router.push("/play/upgrade/");
          return;
        }

        if (event.type === "hit") {
          setOverInfo(event);
          setRuns((previous) =>
            [
              ...previous,
              {
                type: "over",
                mode: event.mode || nextEngine.mode,
                level: event.level,
                score: event.score,
                runScore: event.runScore,
                runCoins: event.runCoins,
                coins: event.coins,
                best: event.best,
                at: Date.now(),
              },
            ].slice(-20),
          );
          // Automatic, not a button on the game-over screen: a record is
          // submitted the moment the run ends, which is the moment it exists.
          void submitRunRecord(event);
          router.push("/play/game-over/");
          return;
        }

        if (event.type === "noHearts") showToast(tRef.current.noHearts);
        if (event.type === "boss") showToast(tRef.current.bossWarn);
        if (event.type === "recordBeaten") showToast(tRef.current.newRecord);
        if (event.type === "levelStarted" || event.type === "runStarted") {
          setHasMoved(false);
        }
      },
    });

    engineRef.current = nextEngine;
    setEngine(nextEngine);
    setSnap(nextEngine.snapshot());
    setName(saved.name || "");

    return () => {
      nextEngine.destroy();
      engineRef.current = null;
      setEngine(null);
    };
  }, [router]);

  useEffect(() => {
    const stored = localStorage.getItem("bb-lang");
    if (stored === "en") setLang("en");
    setStandalone(
      typeof window !== "undefined"
        ? Boolean(window.matchMedia?.("(display-mode: standalone)")?.matches)
        : false,
    );
  }, []);

  // A hidden tab used to keep simulating and drawing at 60fps. On a phone that
  // means a hot battery and a guaranteed frame drop the moment the player
  // comes back. Freeze the run instead; the game page already has a resume
  // overlay for this state.
  useEffect(() => {
    const onVisibility = () => {
      if (!document.hidden) return;
      const current = engineRef.current;
      if (current && current.status === "playing" && !current.paused) current.pause();
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, []);

  useEffect(() => {
    const onPrompt = (event) => {
      event.preventDefault();
      setInstallPrompt(event);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "fa" ? "rtl" : "ltr";
    const current = engineRef.current;
    if (current) current.lang = lang;
    localStorage.setItem("bb-lang", lang);
  }, [lang]);

  useEffect(() => {
    const isGame = /^\/play\/game(\/|$)/.test(pathname || "");
    const current = engineRef.current;
    if (!current) return;

    current.setActive(isGame);
    if (isGame && current.status === "paused") current.resume();
    if (!isGame && current.status === "playing") current.pause();
  }, [pathname]);

  const toggleLang = () => setLang((previous) => (previous === "fa" ? "en" : "fa"));

  const handleSound = () => {
    const current = engineRef.current;
    if (!current) return;
    current.initAudio?.();
    current.setSound(!snapRef.current.sound);
  };

  const handleName = (value) => {
    const current = engineRef.current;
    if (!current) return;
    current.setName(value);
    setNameState(value);
  };

  const handlePlay = (runMode) => {
    const current = engineRef.current;
    if (!current) return;
    current.initAudio?.();
    // React passes a click event when a button uses `onClick={handlePlay}`.
    // Accept only a real mode string; anything else keeps the current mode
    // instead of leaking an event object into the engine.
    const mode = runMode === "record" || runMode === "stage" ? runMode : current.mode || "stage";
    if (!current.startRun(mode)) {
      showToast(tRef.current.noHearts);
      return;
    }
    setCompleteInfo(null);
    setOverInfo(null);
    setSubmitInfo(null);
    beginRunSession(mode);
    router.push("/play/game/");
  };

  const handleRetry = (runMode) => {
    const current = engineRef.current;
    if (!current) return;
    current.initAudio?.();
    const mode = runMode === "record" || runMode === "stage" ? runMode : current.mode || "stage";
    if (!current.startRun(mode)) {
      showToast(tRef.current.noHearts);
      return;
    }
    setCompleteInfo(null);
    setOverInfo(null);
    setSubmitInfo(null);
    beginRunSession(mode);
    router.push("/play/game/");
  };

  const handleQuit = () => {
    const current = engineRef.current;
    if (!current) return;
    // Leaving from the completion card is still a finished attempt. Submit the
    // record the player just earned before the engine resets its run state.
    if (current.mode === "record" && current.status === "complete") {
      void submitRunRecord({ level: current.level, runScore: current.runScore });
    }
    current.quitToMenu();
    setCompleteInfo(null);
    setOverInfo(null);
    setSubmitInfo(null);
    router.push("/play/");
  };

  const handleInstall = () => {
    if (!installPrompt) {
      showToast(tRef.current.installHint);
      return;
    }
    installPrompt.prompt();
    installPrompt.userChoice
      .then((choice) => {
        showToast(
          choice?.outcome === "accepted"
            ? tRef.current.installDone
            : tRef.current.installLater,
        );
      })
      .catch(() => showToast(tRef.current.installLater));
    setInstallPrompt(null);
  };

  const handleNext = () => {
    const current = engineRef.current;
    if (!current) return;
    current.nextLevel();
    router.push("/play/game/");
  };

  const openShop = () => {
    const current = engineRef.current;
    if (!current) return;
    if (current.mode === "record" && current.status === "complete") {
      void submitRunRecord({ level: current.level, runScore: current.runScore });
    }
    if (current.status === "playing") current.pause();
    if (current.status === "complete" || current.status === "hit") current.quitToMenu();
    setCompleteInfo(null);
    setOverInfo(null);
    router.push("/play/shop/");
  };

  const handleBuy = (id) => {
    const current = engineRef.current;
    const currentSave = snapRef.current;
    const def = UPGRADE_DEFS.find((upgrade) => upgrade.id === id);
    if (!current || !def) return;

    const level = currentSave.levelUpgrades?.[id] ?? 0;
    const cost = upgradeCost(id, level);
    const progressionLevel = current.progressionLevel();

    if (progressionLevel < def.unlock) {
      showToast(`${tRef.current.locked} — ${tRef.current.unlockAt} ${formatNumber(def.unlock, lang)}`);
      return;
    }
    if (level >= def.max) {
      showToast(tRef.current.maxed);
      return;
    }
    if (!Number.isFinite(cost) || (currentSave.coins || 0) < cost) {
      showToast(tRef.current.notEnough);
      return;
    }

    if (current.buyUpgrade(id)) {
      showToast(tRef.current.bought);
    } else {
      showToast(tRef.current.notEnough);
    }
  };

  const value = useMemo(
    () => ({
      engine,
      snap,
      lang,
      t,
      name,
      setName: handleName,
      toast,
      completeInfo,
      overInfo,
      submitInfo,
      installPrompt,
      standalone,
      hasMoved,
      setHasMoved,
      runs,
      showToast,
      toggleLang,
      handleSound,
      handleName,
      handlePlay,
      handleRetry,
      handleQuit,
      handleNext,
      openShop,
      handleBuy,
      handleInstall,
      retrySubmit,
    }),
    [
      engine,
      snap,
      lang,
      t,
      name,
      toast,
      completeInfo,
      overInfo,
      submitInfo,
      installPrompt,
      standalone,
      hasMoved,
      runs,
      showToast,
      toggleLang,
      handleSound,
      handleName,
      handlePlay,
      handleRetry,
      handleQuit,
      handleNext,
      openShop,
      handleBuy,
      handleInstall,
      retrySubmit,
    ],
  );

  return <PlayContext.Provider value={value}>{children}</PlayContext.Provider>;
}
