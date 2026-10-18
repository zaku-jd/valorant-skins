package app.quietshop;

/** Shared rotation arithmetic, kept independent of Android for host-side checks. */
final class SkinLogic {
    static long expiresAt(long checkedAt,long remainingSeconds){return checkedAt+Math.max(0,remainingSeconds)*1000L;}
    static long remaining(long end,long now){return Math.max(0,(end-now+999)/1000);}
    static boolean current(long checked,long end,long now){return checked>0&&now<end;}
    static int priceSort(int price,boolean descending){if(price<=0||price==Integer.MAX_VALUE)return Integer.MAX_VALUE;return descending?-price:price;}
}
