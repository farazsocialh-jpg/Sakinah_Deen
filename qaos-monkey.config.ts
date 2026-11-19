/**
 * QAosMonkey config for the Sakinah Deen Android app.
 *
 * The agent explores the installed debug build on its own (no test scripts), driven by a
 * vision-capable OpenAI-compatible model, and writes a Markdown report plus screenshots to
 * `.qaos-monkey/runs/<runId>/`.
 *
 * Run locally:
 *   OPENAI_API_KEY=sk-... npx qaosmonkey run --config qaos-monkey.config.ts
 *
 * CI: .github/workflows/uiux-audit.yml boots an emulator, installs app-debug.apk and runs this.
 */
const emulatorSerial = "emulator-5554";
const appPackage = "com.imancore.app";

const agentDevice = [
  "npx",
  "agent-device",
  "--platform",
  "android",
  "--serial",
  emulatorSerial
];

const config = {
  app: {
    platform: "android",
    name: "Sakinah Deen",
    packageName: appPackage,
    // Launched by the runner; `agent-device open` is forced to use --relaunch.
    launchCommand: [...agentDevice, "open", appPackage, "--relaunch"]
  },
  device: {
    driver: "agent-device",
    command: agentDevice,
    id: emulatorSerial,
    orientation: "portrait",
    resetBeforeRun: false
  },
  model: {
    // "openai" is spelled "openai-compatible" in QAosMonkey; gpt-4o sees the screenshots.
    provider: "openai-compatible",
    model: "gpt-4o",
    baseUrl: "https://api.openai.com/v1",
    apiKeyEnv: "OPENAI_API_KEY",
    temperature: 0,
    maxContextMessages: 30,
    vision: true
  },
  credentials: {
    // The app is usable signed-out; add accounts here only if the audit must sign in.
    accounts: []
  },
  exploration: {
    goal:
      "Audit the UI/UX of every reachable screen: check accessibility labels and content " +
      "descriptions, touch target sizes, text contrast, navigation and back-stack behaviour, " +
      "loading/empty/error states, and anything that looks broken, clipped or unreachable. " +
      "Record a finding for each issue instead of only summarising it.",
    persona:
      "You are an autonomous mobile QA agent auditing a faith/prayer companion app. Be curious, " +
      "systematic and adversarial about edge cases, but never destructive: do not change prayer " +
      "times the user depends on, do not delete data, and never enter payment or donation flows.",
    strategy: "coverage-guided",
    seed: 0,
    mustTest: [
      "Onboarding or first-run experience: every step is readable, skippable, and has a clear primary action.",
      "Salah / prayer times screen: today's timings are visible, and the screen explains what to do when no location is set.",
      "Qibla screen: the compass and its permission/location states are understandable without a sensor fix.",
      "Quran reader: open a surah and check that text is not clipped and the reading controls are tappable.",
      "Tasbih / dhikr counter: increment and reset work and the count is announced accessibly.",
      "Home screen widget configuration screen: all four widget styles are selectable and the previews render.",
      "Settings > Appearance: switch palette and light/dark mode and confirm the app re-themes without restarting.",
      "Back navigation from every deep screen returns to where the user came from, with no empty or duplicated screens."
    ],
    maxSteps: 45,
    minimumStepsBeforeFinish: 10,
    timeLimitSeconds: 1200,
    destructiveLevel: "low",
    maxRepeatedScreenVisits: 4,
    excludedActions: [],
    // Financial and account-destructive areas are out of scope for an exploratory UI audit.
    excludedScreens: ["Zakat", "Donation", "Donate", "Payment", "Delete account", "Sign out"],
    allowlistRiskyAreas: []
  },
  humanInput: {
    // CI cannot answer prompts: the runner ends the run instead of hanging on an ask_human step.
    provider: "cli"
  },
  reporting: {
    outputDir: ".qaos-monkey/runs",
    retainScreenshots: true
  }
};

export default config;
