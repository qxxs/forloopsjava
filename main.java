
import java.util.ArrayList;

public class main   {
    public static void main(String[] args)  {
        ArrayList<Integer> list = new ArrayList<Integer>();

        for (int i = 0; i <= 10; i++)   {
            list.add(i);
        }

        int sum = 0;
        for (int n : list)   {
            sum += n;
        }

        System.out.println("Sum is " + sum);
    }
}
        /* for (int i = 0; i <= 50; i++)  {
            if (i %  == 0) {
                continue;
            } else {
                list.add(i);
            }
        }

        for (int i : list)  {
            System.out.println(i);
        }

    }
}*/