import com.shawnxe7en.myowntv.Tokens;

public class TokensTest {
    static void eq(String name, int want, int got) {
        if (want != got) { System.out.printf("FAIL %s: want %08X got %08X%n", name, want, got); System.exit(1); }
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
        System.out.println("PASS");
    }
}
