import React, { useState } from 'react';
import {
  Search,
  BookOpen,
  Bookmark,
  Share2,
  Copy,
  Check,
  Download,
  Filter,
  CheckCircle2,
  Sparkles,
  ExternalLink
} from 'lucide-react';
import { CANONICAL_BOOKS, SAMPLE_HADITHS } from '../data/islamicData';
import { HadithItem } from '../types';
import { soundEffects } from '../utils/audio';

export const HadithScreen: React.FC = () => {
  const [selectedBookKey, setSelectedBookKey] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [filterGrade, setFilterGrade] = useState<'all' | 'sahih' | 'chapters'>('all');
  const [hadiths, setHadiths] = useState<HadithItem[]>(SAMPLE_HADITHS);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [books, setBooks] = useState(CANONICAL_BOOKS);

  // Toggle download for offline caching simulation
  const toggleDownloadBook = (bookKey: string) => {
    soundEffects.playHapticTap();
    setBooks((prev) =>
      prev.map((b) => (b.key === bookKey ? { ...b, downloaded: !b.downloaded } : b))
    );
  };

  const handleToggleBookmark = (id: string) => {
    soundEffects.playHapticTap();
    setHadiths((prev) =>
      prev.map((h) => (h.id === id ? { ...h, bookmarked: !h.bookmarked } : h))
    );
  };

  const handleCopyHadith = (hadith: HadithItem) => {
    soundEffects.playHapticTap();
    const copyText = `${hadith.bookTitle} #${hadith.hadithNumber}\nGrade: ${hadith.grade} (${hadith.gradingScholar})\n\n${hadith.arabicText}\n\nTranslation: ${hadith.matnTranslation}`;
    navigator.clipboard.writeText(copyText);
    setCopiedId(hadith.id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const filteredHadiths = hadiths.filter((h) => {
    if (selectedBookKey !== 'all' && h.bookKey !== selectedBookKey) return false;
    if (filterGrade === 'sahih' && h.grade !== 'SAHIH') return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchMatn = h.matnTranslation.toLowerCase().includes(q);
      const matchArabic = h.arabicText.includes(searchQuery);
      const matchChapter = h.chapterTitle.toLowerCase().includes(q);
      const matchBook = h.bookTitle.toLowerCase().includes(q);
      return matchMatn || matchArabic || matchChapter || matchBook;
    }
    return true;
  });

  return (
    <div className="flex-1 flex flex-col p-4 sm:p-5 bg-[#F9FAF7] text-[#1E293B] space-y-4 pb-20">
      {/* Header */}
      <div>
        <span className="text-xs font-bold uppercase tracking-wider text-[#10B981]">
          Kutub al-Tis‘ah Encyclopedia
        </span>
        <h2 className="text-xl font-display font-bold text-[#0B3B2C]">
          The Nine Canonical Sunni Hadith Compilations
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Authentic narrations verified by classical Hadith scholarship.
        </p>
      </div>

      {/* 1. Canonical Books Horizontal Carousel */}
      <div className="space-y-1.5">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span className="font-semibold text-slate-700">Canonical Books:</span>
          <span>{books.filter((b) => b.downloaded).length} Downloaded</span>
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
          {/* All Books Filter Chip */}
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setSelectedBookKey('all');
            }}
            className={`px-3 py-2 rounded-2xl text-xs font-bold whitespace-nowrap transition-all border shrink-0 ${
              selectedBookKey === 'all'
                ? 'bg-[#0B3B2C] text-white border-[#0B3B2C] shadow-xs'
                : 'bg-white text-slate-700 border-slate-200 hover:border-slate-300'
            }`}
          >
            All Books
          </button>

          {books.map((book) => {
            const isSelected = selectedBookKey === book.key;
            return (
              <div
                key={book.key}
                onClick={() => {
                  soundEffects.playHapticTap();
                  setSelectedBookKey(book.key);
                }}
                className={`p-3 rounded-2xl border transition-all cursor-pointer shrink-0 min-w-[155px] ${
                  isSelected
                    ? 'bg-emerald-50 border-[#10B981] shadow-xs ring-1 ring-[#10B981]'
                    : 'bg-white border-[#E2E8F0] hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="text-[10px] text-slate-500 font-mono">
                    {book.count.toLocaleString()} Hadiths
                  </span>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      toggleDownloadBook(book.key);
                    }}
                    title={book.downloaded ? 'Downloaded offline' : 'Download book'}
                    className={`p-1 rounded-full text-[10px] ${
                      book.downloaded
                        ? 'text-emerald-700 bg-emerald-100'
                        : 'text-slate-400 hover:text-slate-700'
                    }`}
                  >
                    {book.downloaded ? (
                      <CheckCircle2 className="w-3.5 h-3.5" />
                    ) : (
                      <Download className="w-3.5 h-3.5" />
                    )}
                  </button>
                </div>
                <div className="font-bold text-xs text-[#0B3B2C] truncate">{book.title}</div>
                <div className="text-[10px] text-slate-500 truncate">{book.author}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 2. Full-Text Search Bar */}
      <div className="space-y-2">
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search Hadith by English meaning or Arabic keyword..."
            className="w-full pl-10 pr-4 py-2.5 rounded-2xl bg-white border border-[#E2E8F0] text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#10B981]/40 shadow-xs"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 text-xs hover:text-slate-600"
            >
              Clear
            </button>
          )}
        </div>

        {/* Filter Pills: [All Books] [Sahih Only] [Chapters] */}
        <div className="flex items-center gap-1.5 text-xs">
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setFilterGrade('all');
            }}
            className={`px-3 py-1 rounded-xl font-medium transition-all ${
              filterGrade === 'all'
                ? 'bg-[#0B3B2C] text-white shadow-xs font-bold'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            All Grades
          </button>
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setFilterGrade('sahih');
            }}
            className={`px-3 py-1 rounded-xl font-medium transition-all ${
              filterGrade === 'sahih'
                ? 'bg-[#0B3B2C] text-white shadow-xs font-bold'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            Sahih Only (Authentic)
          </button>
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setFilterGrade('chapters');
            }}
            className={`px-3 py-1 rounded-xl font-medium transition-all ${
              filterGrade === 'chapters'
                ? 'bg-[#0B3B2C] text-white shadow-xs font-bold'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            By Chapter
          </button>
        </div>
      </div>

      {/* 3. Hadith Display Cards */}
      <div className="space-y-3">
        <div className="flex items-center justify-between text-xs text-slate-500 px-1">
          <span>Results: {filteredHadiths.length} narrations found</span>
        </div>

        {filteredHadiths.length === 0 ? (
          <div className="p-8 text-center bg-white rounded-3xl border border-slate-200 space-y-2">
            <BookOpen className="w-8 h-8 text-slate-300 mx-auto" />
            <h4 className="font-bold text-sm text-[#0B3B2C]">No matching Hadith found</h4>
            <p className="text-xs text-slate-500">
              Try searching with broader terms like "intentions", "salah", or "fasting".
            </p>
          </div>
        ) : (
          filteredHadiths.map((hadith) => {
            return (
              <div
                key={hadith.id}
                className="bg-white rounded-3xl border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3 transition-all hover:border-slate-300"
              >
                {/* Top Row: Book Name, Chapter Title, Hadith Number & Grading Badge */}
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="font-bold text-sm text-[#0B3B2C]">
                        {hadith.bookTitle}
                      </span>
                      <span className="font-mono text-xs font-bold text-emerald-800">
                        #{hadith.hadithNumber}
                      </span>
                    </div>
                    <span className="text-[11px] text-slate-500 block mt-0.5">
                      {hadith.chapterTitle}
                    </span>
                  </div>

                  {/* Authenticity Badge */}
                  <div className="text-right shrink-0">
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                        hadith.grade === 'SAHIH'
                          ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                          : hadith.grade === 'HASAN'
                          ? 'bg-teal-100 text-teal-800 border border-teal-300'
                          : 'bg-amber-100 text-amber-800 border border-amber-300'
                      }`}
                    >
                      {hadith.grade}
                    </span>
                    <span className="text-[9px] text-slate-400 block mt-0.5">
                      {hadith.gradingScholar.split(' ')[0]}
                    </span>
                  </div>
                </div>

                {/* Sanad (Isnad Chain) */}
                <div className="text-[11px] text-slate-500 italic bg-slate-50 p-2.5 rounded-xl border border-slate-100">
                  <span className="font-semibold text-slate-700 not-italic">Sanad: </span>
                  {hadith.sanad}
                </div>

                {/* Arabic Text Block with Vowel Markings (Tashkeel) */}
                <div className="p-3.5 bg-emerald-50/50 rounded-2xl border border-emerald-100 text-right">
                  <p className="font-arabic text-xl sm:text-2xl text-[#0B3B2C] leading-loose">
                    {hadith.arabicText}
                  </p>
                </div>

                {/* Translated Matn */}
                <div>
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 block">
                    Matn (Translated Meaning):
                  </span>
                  <p className="text-xs text-slate-800 leading-relaxed font-medium mt-1">
                    "{hadith.matnTranslation}"
                  </p>
                </div>

                {/* Action Bar: Bookmark, Copy, Share, Read Full Chapter */}
                <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs">
                  <div className="flex items-center gap-1.5">
                    {/* Bookmark */}
                    <button
                      type="button"
                      onClick={() => handleToggleBookmark(hadith.id)}
                      className={`p-2 rounded-xl border transition-colors flex items-center gap-1 ${
                        hadith.bookmarked
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                          : 'bg-white text-slate-500 border-slate-200 hover:text-slate-800'
                      }`}
                    >
                      <Bookmark className={`w-3.5 h-3.5 ${hadith.bookmarked ? 'fill-current' : ''}`} />
                      <span className="text-[11px] hidden sm:inline">
                        {hadith.bookmarked ? 'Saved' : 'Save'}
                      </span>
                    </button>

                    {/* Copy */}
                    <button
                      type="button"
                      onClick={() => handleCopyHadith(hadith)}
                      className="p-2 rounded-xl border border-slate-200 hover:border-slate-300 bg-white text-slate-600 hover:text-slate-900 flex items-center gap-1"
                    >
                      {copiedId === hadith.id ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-emerald-600" />
                          <span className="text-[11px] text-emerald-700 font-bold">Copied</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          <span className="text-[11px] hidden sm:inline">Copy</span>
                        </>
                      )}
                    </button>
                  </div>

                  {/* Read Full Chapter */}
                  <button
                    type="button"
                    onClick={() => {
                      soundEffects.playHapticTap();
                      alert(`Opening complete chapter: "${hadith.chapterTitle}" from ${hadith.bookTitle}`);
                    }}
                    className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-[#0B3B2C] text-xs font-semibold flex items-center gap-1"
                  >
                    <span>Read Chapter</span>
                    <ExternalLink className="w-3 h-3 text-slate-500" />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
