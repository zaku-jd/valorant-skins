package app.quietshop;
import java.util.*;
public class SkinLogicTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        long checked=1735689600000L,end=SkinLogic.expiresAt(checked,86400);
        check(end==checked+86400000L,"Rotation must expire 24 hours after sample");
        check(SkinLogic.remaining(end,end-1001)==2,"Countdown rounds partial seconds up");
        check(SkinLogic.remaining(end,end+10000)==0,"Expired countdown cannot be negative");
        check(!SkinLogic.current(checked,end,end),"At the refresh boundary cached items are stale");
        check(!SkinLogic.current(0,100000,100),"An uninitialized shop cannot be current");
        List<Integer> prices=new ArrayList<>(List.of(875,0,2475,1775));prices.sort(Comparator.comparingInt(p->SkinLogic.priceSort(p,true)));
        check(prices.equals(List.of(2475,1775,875,0)),"Unknown prices must stay last when descending");
        prices.sort(Comparator.comparingInt(p->SkinLogic.priceSort(p,false)));check(prices.equals(List.of(875,1775,2475,0)),"Unknown prices must stay last when ascending");
        System.out.println("Rotation expiry, countdown boundaries and price-order checks passed.");
    }
}
