package com.family.ringtoneguard;

public final class GuardPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(GuardPolicy.target(15, 80) == 12, "80% of 15");
        check(GuardPolicy.target(7, 80) == 6, "Round up to minimum");
        check(GuardPolicy.target(0, 80) == 0, "Fixed volume device");
        for (int max = 1; max <= 100; max++) {
            int previous = 0;
            for (int percent = 10; percent <= 100; percent += 10) {
                int value = GuardPolicy.target(max, percent);
                check(value >= 1 && value <= max, "Valid platform range");
                check(value >= previous, "Monotonic target");
                check(value * 100 >= max * percent, "Never round below minimum");
                previous = value;
            }
            check(GuardPolicy.target(max, -10) >= 1, "Clamp invalid preference");
            check(GuardPolicy.target(max, 200) == max, "Clamp above maximum");
        }
        for (boolean override : new boolean[]{false, true}) {
            for (boolean access : new boolean[]{false, true}) {
                check(GuardPolicy.mayCorrect(false, override, access), "DND off allows correction");
                check(GuardPolicy.mayCorrect(true, override, access) == (override && access), "DND requires explicit opt-in AND access");
            }
        }
        System.out.println("GuardPolicy: all tests passed.");
    }
}
