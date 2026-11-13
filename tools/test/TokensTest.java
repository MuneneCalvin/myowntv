import com.shawnxe7en.myowntv.Tokens;

public class TokensTest {
    static void eq(String name, int want, int got) {
        if (want != got) { System.out.printf("FAIL %s: want %08X got %08X%n", name, want, got); System.exit(1); }
    }
    static void near(String name, float want, float got) {
        if (Math.abs(want - got) > 1e-4f) { System.out.println("FAIL " + name + ": want " + want + " got " + got); System.exit(1); }
    }
    public static void main(String[] a) {
        eq("bg", 0xFF0B0B0E, Tokens.BG);
        eq("text", 0xFFF5F5F7, Tokens.TEXT);
        eq("text2 62%", 0x9EF5F5F7, Tokens.TEXT2);
        eq("text3 42%", 0x6BF5F5F7, Tokens.TEXT3);
        eq("tile", 0xFF1C1C21, Tokens.TILE);
        eq("safeX", 80, (int) Tokens.SAFE_X);
        eq("safeY", 60, (int) Tokens.SAFE_Y);
        eq("gap", 40, (int) Tokens.GAP);
        eq("alpha", 0x80123456, Tokens.alpha(0xFF123456, 0.5f));
        near("veil top", 0.24f, Tokens.veil(0));
        near("veil mid", 0.06f, Tokens.veil(0.5f));
        near("veil bottom", 0.12f, Tokens.veil(1));
        near("veil quarter", 0.15f, Tokens.veil(0.25f));
        near("saturate", 1.7f, Tokens.GLASS_SATURATE);
        near("blur", 28f, Tokens.GLASS_BLUR_PX);
        eq("glass tint", 0xFF14141C, Tokens.GLASS_TINT);
        near("glass tint alpha", 0.22f, Tokens.GLASS_TINT_ALPHA);
        System.out.println("PASS");
    }
}
