/**
 * Web Audio API synthesizer for clean Islamic prayer chimes, Adhan melodies,
 * Takbir reminders, recitation tones, and tactile haptic clicks.
 * 100% synthesized - zero external audio assets.
 */

class AudioSynthesizer {
  private ctx: AudioContext | null = null;

  private initCtx() {
    if (!this.ctx && typeof window !== 'undefined') {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  // Soft tactile haptic click for prayer check, Qadha decrement, or stepper
  playHapticTap() {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(420, this.ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(140, this.ctx.currentTime + 0.04);
      gain.gain.setValueAtTime(0.18, this.ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.04);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start();
      osc.stop(this.ctx.currentTime + 0.04);
    } catch {
      // AudioContext policy fallback
    }
  }

  // Pleasant bell chime for Takbir / Dua recitation completion
  playTakbirChime() {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const frequencies = [523.25, 659.25, 783.99, 1046.50]; // C5, E5, G5, C6
      frequencies.forEach((freq, idx) => {
        if (!this.ctx) return;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(freq, now + idx * 0.08);
        gain.gain.setValueAtTime(0.12, now + idx * 0.08);
        gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.08 + 0.6);
        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(now + idx * 0.08);
        osc.stop(now + idx * 0.08 + 0.65);
      });
    } catch {
      // ignore
    }
  }

  // Soothing harmonic Adhan chime motif (Maqam Bayati tone)
  playAdhanMotif(onDone?: () => void) {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const notes = [
        { f: 293.66, d: 0.5 }, // D4
        { f: 329.63, d: 0.4 }, // E4
        { f: 349.23, d: 0.6 }, // F4
        { f: 392.00, d: 0.7 }, // G4
        { f: 349.23, d: 0.4 }, // F4
        { f: 329.63, d: 0.5 }, // E4
        { f: 293.66, d: 1.1 }, // D4
      ];
      let t = this.ctx.currentTime;
      notes.forEach((note) => {
        if (!this.ctx) return;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(note.f, t);
        gain.gain.setValueAtTime(0.15, t);
        gain.gain.exponentialRampToValueAtTime(0.001, t + note.d);
        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(t);
        osc.stop(t + note.d + 0.05);
        t += note.d * 0.85;
      });
      if (onDone) {
        setTimeout(onDone, 3800);
      }
    } catch {
      if (onDone) onDone();
    }
  }

  // Sadaqah coin drop metallic chime
  playCoinChime() {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const osc1 = this.ctx.createOscillator();
      const osc2 = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc1.type = 'sine';
      osc2.type = 'triangle';
      osc1.frequency.setValueAtTime(987.77, now); // B5
      osc1.frequency.exponentialRampToValueAtTime(1318.51, now + 0.1); // E6
      osc2.frequency.setValueAtTime(1975.53, now); // B6
      gain.gain.setValueAtTime(0.2, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
      osc1.connect(gain);
      osc2.connect(gain);
      gain.connect(this.ctx.destination);
      osc1.start(now);
      osc2.start(now);
      osc1.stop(now + 0.45);
      osc2.stop(now + 0.45);
    } catch {
      // ignore
    }
  }

  // Recitation phonetic audio simulator with rhythmic calm chant
  playRecitation(durationSeconds: number = 3, onDone?: () => void) {
    try {
      this.initCtx();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      // Slight pitch vibrato for Quranic recitation style
      osc.frequency.setValueAtTime(261.63, now); // C4
      osc.frequency.linearRampToValueAtTime(293.66, now + durationSeconds * 0.3);
      osc.frequency.linearRampToValueAtTime(261.63, now + durationSeconds * 0.7);
      osc.frequency.linearRampToValueAtTime(246.94, now + durationSeconds);
      gain.gain.setValueAtTime(0.01, now);
      gain.gain.linearRampToValueAtTime(0.14, now + 0.2);
      gain.gain.setValueAtTime(0.14, now + durationSeconds - 0.3);
      gain.gain.linearRampToValueAtTime(0.001, now + durationSeconds);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(now);
      osc.stop(now + durationSeconds + 0.1);
      if (onDone) {
        setTimeout(onDone, durationSeconds * 1000);
      }
    } catch {
      if (onDone) onDone();
    }
  }
}

export const soundEffects = new AudioSynthesizer();
