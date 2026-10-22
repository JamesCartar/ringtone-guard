package com.family.ringtoneguard;

final class GuardPolicy {
    static int target(int max, int percent) {
        if (max < 1) return 0;
        int safePercent = Math.max(10, Math.min(100, percent));
        return Math.min(max, Math.max(1, (max * safePercent + 99) / 100));
    }
    static boolean mayCorrect(boolean dndActive, boolean overrideDnd, boolean hasAccess) {
        return !dndActive || (overrideDnd && hasAccess);
    }
}
