
import java.util.ArrayList;

public class main   {
    public static void main(String[] args)  {
        ArrayList<Integer> list = new ArrayList<Integer>();

        for (int i = 20; i >= 0; i-=1)  {
            if (i % 2 == 0) {
                list.add(i);
            }
        }

        for (int i : list)  {
            System.out.println(i);
        }

    }
}