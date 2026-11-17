"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ChevronDown, HelpCircle, Search, X } from "lucide-react";

import {
  FAQ_CATEGORIES,
  FAQ_ITEMS,
  filterFaqItems,
  type FaqCategoryFilter,
} from "@/lib/faqData";
import {
  ExpandablePanel,
  getAlternatingDirection,
  Reveal,
  SignalNetworkVisual,
  useReducedMotion,
} from "./MotionPrimitives";

function AnimatedAnswerText({
  text,
  isOpen,
}: {
  text: string;
  isOpen: boolean;
}) {
  const reducedMotion = useReducedMotion();
  const words = useMemo(() => text.split(/\s+/).filter(Boolean), [text]);

  if (reducedMotion) {
    return <span>{text}</span>;
  }

  return (
    <span>
      {words.map((word, index) => (
        <span
          key={`${index}-${word}`}
          style={{
            transitionDelay: isOpen ? `${Math.min(index * 12, 420)}ms` : "0ms",
          }}
          className={`wv-faq-word ${
            isOpen ? "wv-faq-word-shown" : "wv-faq-word-hidden"
          }`}
        >
          {word}
          {index < words.length - 1 ? "\u00A0" : ""}
        </span>
      ))}
    </span>
  );
}

export default function FaqContent() {
  const [selectedCategory, setSelectedCategory] =
    useState<FaqCategoryFilter>("All");
  const [searchQuery, setSearchQuery] = useState("");
  const [expandedIds, setExpandedIds] = useState<string[]>(() => [
    FAQ_ITEMS[0].id,
  ]);

  const filteredItems = useMemo(
    () => filterFaqItems(FAQ_ITEMS, selectedCategory, searchQuery),
    [selectedCategory, searchQuery]
  );

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { All: FAQ_ITEMS.length };
    for (const cat of FAQ_CATEGORIES) {
      counts[cat] = FAQ_ITEMS.filter((item) => item.category === cat).length;
    }
    return counts;
  }, []);

  function toggleItem(id: string) {
    setExpandedIds((current) =>
      current.includes(id)
        ? current.filter((item) => item !== id)
        : [...current, id]
    );
  }

  function handleExpandAll() {
    setExpandedIds(filteredItems.map((item) => item.id));
  }

  function handleCollapseAll() {
    setExpandedIds([]);
  }

  function handleResetFilters() {
    setSelectedCategory("All");
    setSearchQuery("");
  }

  const allExpanded =
    filteredItems.length > 0 &&
    filteredItems.every((item) => expandedIds.includes(item.id));

  return (
    <div className="w-full overflow-x-clip">
      {/* Hero */}
      <section className="relative border-b border-[#303640]/[0.08] bg-gradient-to-b from-[#F7F6F2] via-[#F7F6F2] to-[#F1EFE9]/75 py-12 sm:py-16">
        <div className="mx-auto grid w-full max-w-[1200px] grid-cols-1 items-center gap-8 px-4 sm:px-6 lg:grid-cols-[1.15fr_0.85fr]">
          <Reveal direction="bottom" immediate>
            <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/35 bg-white px-3.5 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38] shadow-2xs">
              <HelpCircle size={13} />
              Knowledge Base &amp; Documentation
            </div>

            <h1 className="mt-4 text-3xl font-semibold tracking-tight text-[#303640] sm:text-4xl lg:text-[42px]">
              Questions? We&apos;ve Got{" "}
              <span className="text-[#9F7C38]">Answers.</span>
            </h1>

            <p className="mt-3 max-w-xl text-sm leading-relaxed text-[#5E6572]">
              Accurate explanations of Wi-Fi metrics, GPS uncertainty, Android
              local-first permissions, speed diagnostics, and account security in
              WiFiVision.
            </p>

            {/* Interactive Search Field */}
            <div className="mt-6 max-w-xl">
              <label htmlFor="faq-search" className="sr-only">
                Search frequently asked questions
              </label>
              <div className="relative flex items-center">
                <Search
                  size={17}
                  className="pointer-events-none absolute left-4 text-[#868D98]"
                />
                <input
                  id="faq-search"
                  type="search"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="Search questions (e.g. RSSI, GPS accuracy, upload, OTP)..."
                  className="h-12 w-full rounded-2xl border border-[#303640]/15 bg-white pl-11 pr-11 text-sm text-[#303640] placeholder-[#868D98] shadow-xs outline-none transition focus:border-[#C6A15B] focus:ring-3 focus:ring-[#C6A15B]/15"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => setSearchQuery("")}
                    aria-label="Clear search"
                    className="wv-btn-pill absolute right-3 inline-flex h-7 w-7 items-center justify-center rounded-lg bg-[#F1EFE9] text-[#5E6572] hover:bg-[#ECE9E0] hover:text-[#303640]"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>

              <div
                role="status"
                aria-live="polite"
                className="mt-2.5 flex flex-wrap items-center justify-between gap-2 text-xs text-[#5E6572]"
              >
                <span>
                  Showing{" "}
                  <strong className="font-semibold text-[#303640]">
                    {filteredItems.length}
                  </strong>{" "}
                  of {FAQ_ITEMS.length} questions
                  {selectedCategory !== "All" ? ` in ${selectedCategory}` : ""}
                </span>

                {(selectedCategory !== "All" || searchQuery.trim()) && (
                  <button
                    type="button"
                    onClick={handleResetFilters}
                    className="wv-btn-pill rounded-md px-2 py-0.5 font-semibold text-[#9F7C38] hover:bg-[#C6A15B]/12"
                  >
                    Reset filters
                  </button>
                )}
              </div>
            </div>
          </Reveal>

          <Reveal delayMs={140} direction="right" immediate>
            <SignalNetworkVisual compact />
          </Reveal>
        </div>
      </section>

      {/* Main FAQ Workspace */}
      <section className="mx-auto w-full max-w-[1200px] px-4 py-10 sm:px-6 lg:py-14">
        {/* Category Pills */}
        <div
          role="tablist"
          aria-label="FAQ categories"
          className="flex flex-wrap items-center gap-2"
        >
          {(["All", ...FAQ_CATEGORIES] as const).map((category) => {
            const active = selectedCategory === category;
            return (
              <button
                key={category}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setSelectedCategory(category)}
                className={`wv-btn-pill inline-flex items-center gap-1.5 rounded-xl border px-3.5 py-2 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B] ${
                  active
                    ? "border-[#C6A15B] bg-[#303640] text-white shadow-xs"
                    : "border-[#303640]/12 bg-white text-[#5E6572] hover:border-[#C6A15B]/50 hover:text-[#303640]"
                }`}
              >
                <span>{category}</span>
                <span
                  className={`rounded-md px-1.5 py-0.5 text-[10px] tabular-nums ${
                    active
                      ? "bg-[#C6A15B]/25 text-[#C6A15B]"
                      : "bg-[#F1EFE9] text-[#5E6572]"
                  }`}
                >
                  {categoryCounts[category]}
                </span>
              </button>
            );
          })}
        </div>

        {/* Expand/Collapse Controls */}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-b border-[#303640]/[0.08] pb-4">
          <p className="text-xs font-medium text-[#5E6572]">
            {selectedCategory === "All"
              ? "All technical and account topics"
              : `${selectedCategory} (${filteredItems.length} question${
                  filteredItems.length === 1 ? "" : "s"
                })`}
          </p>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleExpandAll}
              disabled={filteredItems.length === 0 || allExpanded}
              className="wv-btn-secondary inline-flex items-center justify-center rounded-lg border border-[#303640]/12 bg-white px-3.5 py-1.5 text-[11px] font-semibold text-[#303640] disabled:pointer-events-none disabled:opacity-45"
            >
              Expand all
            </button>
            <button
              type="button"
              onClick={handleCollapseAll}
              disabled={expandedIds.length === 0}
              className="wv-btn-secondary inline-flex items-center justify-center rounded-lg border border-[#303640]/12 bg-white px-3.5 py-1.5 text-[11px] font-semibold text-[#303640] disabled:pointer-events-none disabled:opacity-45"
            >
              Collapse all
            </button>
          </div>
        </div>

        {/* Accordion List (Scroll-Revealed with Alternating Directions) */}
        {filteredItems.length > 0 ? (
          <div className="mt-6 space-y-3">
            {filteredItems.map((item, idx) => {
              const isOpen = expandedIds.includes(item.id);
              const buttonId = `faq-btn-${item.id}`;
              const panelId = `faq-panel-${item.id}`;

              return (
                <Reveal
                  key={`${selectedCategory}-${item.id}`}
                  direction={getAlternatingDirection(idx, 2)}
                  delayMs={Math.min((idx % 3) * 110, 220)}
                >
                  <article
                    className={`rounded-2xl border bg-white transition-colors duration-200 ${
                      isOpen
                        ? "border-[#C6A15B]/55 shadow-[0_8px_24px_rgba(48,54,64,0.05)]"
                        : "border-[#303640]/[0.09] hover:border-[#C6A15B]/35"
                    }`}
                  >
                    <h3>
                      <button
                        id={buttonId}
                        type="button"
                        aria-expanded={isOpen}
                        aria-controls={panelId}
                        onClick={() => toggleItem(item.id)}
                        className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left transition-colors hover:bg-[#F7F6F2]/60 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[#C6A15B]"
                      >
                        <div className="min-w-0">
                          <span className="inline-block rounded-md bg-[#F1EFE9] px-2 py-0.5 text-[10px] font-semibold text-[#9F7C38]">
                            {item.category}
                          </span>
                          <span className="mt-1.5 block text-sm font-semibold text-[#303640] sm:text-[15px]">
                            {item.question}
                          </span>
                        </div>

                        <span
                          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border transition-transform duration-200 ${
                            isOpen
                              ? "rotate-180 border-[#C6A15B]/45 bg-[#C6A15B]/15 text-[#9F7C38]"
                              : "border-[#303640]/10 bg-[#F7F6F2] text-[#5E6572]"
                          }`}
                        >
                          <ChevronDown size={16} />
                        </span>
                      </button>
                    </h3>

                    <ExpandablePanel
                      id={panelId}
                      isOpen={isOpen}
                      labelledBy={buttonId}
                    >
                      <div className="border-t border-[#303640]/[0.07] px-5 pb-5 pt-3.5 text-sm leading-relaxed text-[#5E6572]">
                        <AnimatedAnswerText text={item.answer} isOpen={isOpen} />
                      </div>
                    </ExpandablePanel>
                  </article>
                </Reveal>
              );
            })}
          </div>
        ) : (
          <div className="mt-8 rounded-2xl border border-dashed border-[#303640]/20 bg-white px-6 py-12 text-center">
            <Search size={28} className="mx-auto text-[#868D98]" />
            <h2 className="mt-3 text-base font-semibold text-[#303640]">
              No matching questions found
            </h2>
            <p className="mx-auto mt-1 max-w-md text-xs leading-relaxed text-[#5E6572]">
              No FAQ entries matched &ldquo;{searchQuery}&rdquo; in{" "}
              {selectedCategory}. Try another term or clear your active filters.
            </p>
            <button
              type="button"
              onClick={handleResetFilters}
              className="wv-btn-primary mt-5 inline-flex h-10 items-center justify-center rounded-xl border border-[#303640] bg-[#303640] px-5 text-xs font-semibold text-white"
            >
              Show all questions
            </button>
          </div>
        )}

        {/* Helpful bottom callout */}
        <Reveal direction="bottom" delayMs={100}>
          <div className="mt-10 flex flex-col justify-between gap-4 rounded-2xl border border-[#303640]/[0.09] bg-[#F1EFE9] p-6 sm:flex-row sm:items-center">
            <div>
              <p className="text-sm font-semibold text-[#303640]">
                Want to review how telemetry and account data are handled?
              </p>
              <p className="mt-1 text-xs text-[#5E6572]">
                Read our complete Privacy Policy or learn more about WiFiVision.
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2.5">
              <Link
                href="/privacy-policy"
                className="wv-btn-secondary inline-flex h-10 items-center justify-center rounded-xl border border-[#303640]/14 bg-white px-4 text-xs font-semibold text-[#303640]"
              >
                Privacy Policy
              </Link>
              <Link
                href="/register"
                className="wv-btn-primary inline-flex h-10 items-center justify-center rounded-xl border border-[#303640] bg-[#303640] px-4 text-xs font-semibold text-white"
              >
                Create Account
              </Link>
            </div>
          </div>
        </Reveal>
      </section>
    </div>
  );
}
