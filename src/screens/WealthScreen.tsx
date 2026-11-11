import React, { useState } from 'react';
import {
  Coins,
  TrendingUp,
  RotateCcw,
  Download,
  Plus,
  Heart,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  ArrowRight
} from 'lucide-react';
import { INITIAL_WEALTH_STATE } from '../data/islamicData';
import { WealthZakatState } from '../types';
import { soundEffects } from '../utils/audio';

export const WealthScreen: React.FC = () => {
  const [wealth, setWealth] = useState<WealthZakatState>(INITIAL_WEALTH_STATE);
  const [showExportModal, setShowExportModal] = useState(false);

  // Nisab threshold calculations
  const goldThresholdValue = 85 * wealth.goldSpotPricePerGram; // 85g gold
  const silverThresholdValue = 595 * wealth.silverSpotPricePerGram; // 595g silver

  const currentNisabThreshold =
    wealth.nisabStandard === 'gold' ? goldThresholdValue : silverThresholdValue;

  // Total Gross Assets
  const grossAssets =
    wealth.cashInHandAndBank +
    wealth.goldValueHeld +
    wealth.silverValueHeld +
    wealth.businessInventory +
    wealth.stocksInvestments;

  // Net Zakat-Eligible Wealth
  const netEligibleWealth = Math.max(0, grossAssets - wealth.immediateDebts);

  // Status
  const isEligible = netEligibleWealth >= currentNisabThreshold;

  // 2.5% Payable Zakat
  const payableZakat = isEligible ? netEligibleWealth * 0.025 : 0;

  // Add Micro-Sadaqah
  const handleAddSadaqah = (amount: number) => {
    soundEffects.playCoinChime();
    setWealth((prev) => ({
      ...prev,
      sadaqahJarCollected: prev.sadaqahJarCollected + amount,
      recentDonations: [
        {
          id: Date.now().toString(),
          amount,
          date: 'Just now',
          note: 'Direct voluntary micro-giving'
        },
        ...prev.recentDonations
      ]
    }));
  };

  const handleReset = () => {
    soundEffects.playHapticTap();
    setWealth(INITIAL_WEALTH_STATE);
  };

  const jarProgressPct = Math.min(
    Math.round((wealth.sadaqahJarCollected / wealth.sadaqahMonthlyGoal) * 100),
    100
  );

  return (
    <div className="flex-1 flex flex-col p-4 sm:p-5 bg-[#F9FAF7] text-[#1E293B] space-y-4 pb-20">
      {/* Top Header */}
      <div>
        <span className="text-xs font-bold uppercase tracking-wider text-[#10B981]">
          Māl, Zakat &amp; Sadaqah Hub
        </span>
        <h2 className="text-xl font-display font-bold text-[#0B3B2C]">
          Multi-Asset Sunni Zakat Calculator
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Calculated according to classical 2.5% (Rub‘ al-‘Ushr) Islamic financial jurisprudence.
        </p>
      </div>

      {/* 1. Nisab Live Valuation Card */}
      <div className="bg-white rounded-3xl border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Coins className="w-5 h-5 text-[#10B981]" />
            <h3 className="font-bold text-sm text-[#0B3B2C]">Nisab Valuation Benchmark</h3>
          </div>

          {/* Gold / Silver Toggle */}
          <div className="flex items-center bg-slate-100 p-1 rounded-xl text-xs border border-slate-200">
            <button
              type="button"
              onClick={() => {
                soundEffects.playHapticTap();
                setWealth((prev) => ({ ...prev, nisabStandard: 'gold' }));
              }}
              className={`px-2.5 py-1 rounded-lg font-semibold transition-all ${
                wealth.nisabStandard === 'gold'
                  ? 'bg-[#0B3B2C] text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Gold (85g)
            </button>
            <button
              type="button"
              onClick={() => {
                soundEffects.playHapticTap();
                setWealth((prev) => ({ ...prev, nisabStandard: 'silver' }));
              }}
              className={`px-2.5 py-1 rounded-lg font-semibold transition-all ${
                wealth.nisabStandard === 'silver'
                  ? 'bg-[#0B3B2C] text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Silver (595g)
            </button>
          </div>
        </div>

        {/* Spot Price details */}
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="p-2.5 bg-slate-50 rounded-2xl border border-slate-200">
            <span className="text-slate-500 block text-[10px]">Spot Gold Price:</span>
            <span className="font-bold font-mono text-[#0B3B2C]">
              ${wealth.goldSpotPricePerGram.toFixed(2)} / g
            </span>
            <span className="text-[10px] text-slate-400 block mt-0.5">
              85g = ${goldThresholdValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}
            </span>
          </div>

          <div className="p-2.5 bg-slate-50 rounded-2xl border border-slate-200">
            <span className="text-slate-500 block text-[10px]">Spot Silver Price:</span>
            <span className="font-bold font-mono text-[#0B3B2C]">
              ${wealth.silverSpotPricePerGram.toFixed(2)} / g
            </span>
            <span className="text-[10px] text-slate-400 block mt-0.5">
              595g = ${silverThresholdValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}
            </span>
          </div>
        </div>

        {/* Status Banner */}
        <div
          className={`p-3 rounded-2xl border flex items-center justify-between text-xs font-semibold ${
            isEligible
              ? 'bg-emerald-50 border-emerald-300 text-[#0B3B2C]'
              : 'bg-slate-50 border-slate-200 text-slate-600'
          }`}
        >
          <div className="flex items-center gap-2">
            {isEligible ? (
              <CheckCircle2 className="w-4 h-4 text-[#10B981]" />
            ) : (
              <AlertCircle className="w-4 h-4 text-slate-400" />
            )}
            <span>
              {isEligible
                ? `Eligible for Zakat (Wealth exceeds $${currentNisabThreshold.toFixed(0)})`
                : `Wealth is below Nisab ($${currentNisabThreshold.toFixed(0)} threshold)`}
            </span>
          </div>
          <span className="font-mono text-[11px] font-bold">
            {wealth.nisabStandard === 'gold' ? 'Standard 85g' : 'Standard 595g'}
          </span>
        </div>
      </div>

      {/* 2. Multi-Asset Balance Sheet Inputs */}
      <div className="bg-white rounded-3xl border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3">
        <h3 className="font-bold text-sm text-[#0B3B2C]">Assets Subject to Zakat (Hawl Year)</h3>

        <div className="space-y-2.5 text-xs">
          {/* Cash */}
          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              1. Cash in Hand &amp; Bank Accounts:
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-slate-400">$</span>
              <input
                type="number"
                value={wealth.cashInHandAndBank}
                onChange={(e) =>
                  setWealth({ ...wealth, cashInHandAndBank: parseFloat(e.target.value) || 0 })
                }
                className="w-full pl-7 pr-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 font-mono font-semibold focus:outline-none focus:ring-2 focus:ring-[#10B981]/40"
              />
            </div>
          </div>

          {/* Gold & Silver Held */}
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[11px] font-semibold text-slate-600 block mb-1">
                2. Gold Value Held:
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-slate-400">$</span>
                <input
                  type="number"
                  value={wealth.goldValueHeld}
                  onChange={(e) =>
                    setWealth({ ...wealth, goldValueHeld: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full pl-7 pr-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 font-mono font-semibold focus:outline-none focus:ring-2 focus:ring-[#10B981]/40"
                />
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-slate-600 block mb-1">
                3. Silver Value Held:
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-slate-400">$</span>
                <input
                  type="number"
                  value={wealth.silverValueHeld}
                  onChange={(e) =>
                    setWealth({ ...wealth, silverValueHeld: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full pl-7 pr-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 font-mono font-semibold focus:outline-none focus:ring-2 focus:ring-[#10B981]/40"
                />
              </div>
            </div>
          </div>

          {/* Business Inventory & Stocks */}
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[11px] font-semibold text-slate-600 block mb-1">
                4. Trade Goods / Inventory:
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-slate-400">$</span>
                <input
                  type="number"
                  value={wealth.businessInventory}
                  onChange={(e) =>
                    setWealth({ ...wealth, businessInventory: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full pl-7 pr-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 font-mono font-semibold focus:outline-none focus:ring-2 focus:ring-[#10B981]/40"
                />
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-slate-600 block mb-1">
                5. Stocks / Liquid Investments:
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-slate-400">$</span>
                <input
                  type="number"
                  value={wealth.stocksInvestments}
                  onChange={(e) =>
                    setWealth({ ...wealth, stocksInvestments: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full pl-7 pr-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 font-mono font-semibold focus:outline-none focus:ring-2 focus:ring-[#10B981]/40"
                />
              </div>
            </div>
          </div>

          {/* Deductible Short-Term Debts */}
          <div>
            <label className="text-[11px] font-semibold text-red-700 block mb-1">
              Less: Deductible Immediate Debts &amp; Due Bills:
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 font-bold text-slate-400">$</span>
              <input
                type="number"
                value={wealth.immediateDebts}
                onChange={(e) =>
                  setWealth({ ...wealth, immediateDebts: parseFloat(e.target.value) || 0 })
                }
                className="w-full pl-7 pr-3 py-2 rounded-xl bg-red-50/50 border border-red-200 text-red-900 font-mono font-semibold focus:outline-none focus:ring-2 focus:ring-red-400/40"
              />
            </div>
          </div>
        </div>
      </div>

      {/* 3. Zakat Summary Breakdown Card */}
      <div className="bg-gradient-to-br from-[#0B3B2C] to-[#134E3A] text-white rounded-3xl p-5 shadow-sm space-y-4 border border-emerald-800">
        <div className="flex items-center justify-between">
          <span className="text-xs uppercase tracking-wider text-[#10B981] font-bold">
            Payable Zakat Summary (2.5%)
          </span>
          <span className="text-xs bg-emerald-950/80 text-emerald-300 px-2 py-0.5 rounded-full border border-emerald-700/50 font-mono">
            Net: ${netEligibleWealth.toLocaleString(undefined, { maximumFractionDigits: 0 })}
          </span>
        </div>

        <div className="flex items-baseline justify-between pt-1">
          <div>
            <div className="text-3xl sm:text-4xl font-display font-bold font-mono">
              ${payableZakat.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </div>
            <p className="text-xs text-emerald-200/80 mt-1">
              Due to the 8 categories of Asnaf (Surah At-Tawbah 9:60)
            </p>
          </div>
        </div>

        {/* Action Buttons: Export Breakdown & Reset */}
        <div className="pt-2 flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setShowExportModal(true);
            }}
            className="flex-1 py-2.5 px-3 rounded-xl bg-[#10B981] hover:bg-[#059669] text-emerald-950 font-bold text-xs flex items-center justify-center gap-1.5 transition-transform active:scale-95"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export Breakdown</span>
          </button>

          <button
            type="button"
            onClick={handleReset}
            className="py-2.5 px-3 rounded-xl bg-white/10 hover:bg-white/20 text-white font-medium text-xs flex items-center justify-center gap-1.5 border border-white/10 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </button>
        </div>
      </div>

      {/* 4. Daily Micro-Sadaqah Jar */}
      <div className="bg-white rounded-3xl border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Heart className="w-4 h-4 text-emerald-600 fill-emerald-600" />
            <h3 className="font-bold text-sm text-[#0B3B2C]">Daily Micro-Sadaqah Jar</h3>
          </div>
          <span className="text-xs font-semibold text-slate-500">
            Monthly Goal: ${wealth.sadaqahMonthlyGoal}
          </span>
        </div>

        {/* Jar Visual & Progress Ring */}
        <div className="p-3 bg-emerald-50/60 rounded-2xl border border-emerald-200 flex items-center gap-4">
          {/* Jar Graphic with Fill Progress */}
          <div className="relative w-14 h-18 bg-white border-2 border-[#0B3B2C] rounded-b-2xl rounded-t-lg overflow-hidden shrink-0 shadow-inner flex flex-col justify-end p-0.5">
            <div className="absolute top-1 left-1/2 -translate-x-1/2 w-8 h-1 bg-[#0B3B2C] rounded-full" />
            <div
              className="w-full bg-[#10B981] rounded-b-xl transition-all duration-500 flex items-center justify-center text-[9px] font-bold text-emerald-950"
              style={{ height: `${jarProgressPct}%` }}
            >
              {jarProgressPct > 15 && `${jarProgressPct}%`}
            </div>
          </div>

          <div className="flex-1 space-y-1">
            <div className="flex items-baseline justify-between">
              <span className="text-xl font-display font-bold font-mono text-[#0B3B2C]">
                ${wealth.sadaqahJarCollected}
              </span>
              <span className="text-xs text-emerald-800 font-semibold font-mono">
                {jarProgressPct}% of Goal
              </span>
            </div>
            <p className="text-[11px] text-slate-600">
              "The most beloved of deeds to Allah are those that are most consistent, even if small."
            </p>
          </div>
        </div>

        {/* Quick +$1, +$5, +$10 Micro-Giving Stepper Buttons */}
        <div className="grid grid-cols-3 gap-2">
          {[1, 5, 10].map((amount) => (
            <button
              key={amount}
              type="button"
              onClick={() => handleAddSadaqah(amount)}
              className="py-2.5 px-3 rounded-xl bg-slate-100 hover:bg-[#10B981] hover:text-emerald-950 text-[#0B3B2C] font-bold text-xs flex items-center justify-center gap-1 transition-all active:scale-95 border border-slate-200"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>+${amount} Sadaqah</span>
            </button>
          ))}
        </div>

        {/* Recent Micro-Donations History */}
        <div className="pt-2 border-t border-slate-100 space-y-1.5">
          <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
            Recent Giving Log:
          </span>
          <div className="space-y-1 max-h-32 overflow-y-auto">
            {wealth.recentDonations.slice(0, 3).map((don) => (
              <div
                key={don.id}
                className="p-2 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between text-xs"
              >
                <div>
                  <span className="font-semibold text-slate-800">{don.note}</span>
                  <span className="text-[10px] text-slate-400 block">{don.date}</span>
                </div>
                <span className="font-mono font-bold text-emerald-700">+${don.amount}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Export Breakdown Modal */}
      {showExportModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-5 w-full max-w-sm space-y-4 shadow-xl border border-slate-200 animate-fadeIn text-xs">
            <h3 className="font-bold text-base text-[#0B3B2C]">Zakat Breakdown Statement</h3>
            <div className="p-3 bg-slate-50 rounded-2xl border border-slate-200 space-y-1 font-mono text-[11px]">
              <div className="flex justify-between">
                <span>Total Gross Assets:</span>
                <span>${grossAssets.toLocaleString()}</span>
              </div>
              <div className="flex justify-between text-red-600">
                <span>Short-Term Debts:</span>
                <span>-${wealth.immediateDebts.toLocaleString()}</span>
              </div>
              <div className="border-t border-slate-200 pt-1 flex justify-between font-bold text-[#0B3B2C]">
                <span>Net Eligible Wealth:</span>
                <span>${netEligibleWealth.toLocaleString()}</span>
              </div>
              <div className="flex justify-between font-bold text-emerald-800">
                <span>Zakat Due (2.5%):</span>
                <span>${payableZakat.toFixed(2)}</span>
              </div>
            </div>
            <p className="text-slate-500 text-[11px]">
              Ready for export to PDF or record keeping for the current Hijri lunar year.
            </p>
            <button
              onClick={() => setShowExportModal(false)}
              className="w-full py-2.5 rounded-xl bg-[#0B3B2C] text-white font-semibold"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
