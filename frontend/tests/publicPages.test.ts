import test from "node:test";
import assert from "node:assert/strict";

import {
  FAQ_CATEGORIES,
  FAQ_ITEMS,
  filterFaqItems,
} from "../src/lib/faqData";

test("FAQ dataset covers all 8 required categories", () => {
  assert.deepEqual([...FAQ_CATEGORIES], [
    "General",
    "Wi-Fi & Networking",
    "Heatmap & GPS",
    "Android Application",
    "Internet Performance",
    "Accounts & Authentication",
    "Privacy & Security",
    "Troubleshooting",
  ]);

  for (const category of FAQ_CATEGORIES) {
    const matches = FAQ_ITEMS.filter((item) => item.category === category);
    assert.ok(
      matches.length >= 1,
      `Expected at least one FAQ item in category "${category}"`
    );
  }
});

test("filterFaqItems filters cleanly by category, search query, and handles empty results", () => {
  assert.equal(filterFaqItems(FAQ_ITEMS, "All", "").length, FAQ_ITEMS.length);

  const gpsItems = filterFaqItems(FAQ_ITEMS, "Heatmap & GPS", "");
  assert.ok(gpsItems.every((item) => item.category === "Heatmap & GPS"));
  assert.ok(gpsItems.some((item) => item.id === "does-pin-mean-router-location"));

  const rssiItems = filterFaqItems(FAQ_ITEMS, "All", "RSSI");
  assert.ok(rssiItems.some((item) => item.id === "what-is-rssi"));

  const none = filterFaqItems(FAQ_ITEMS, "All", "xyznonexistentterm123");
  assert.equal(none.length, 0);
});
