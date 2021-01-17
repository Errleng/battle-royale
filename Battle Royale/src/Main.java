import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        final String normOutputFileName = "norm_output.txt";
        final String optOutputFileName = "opt_output.txt";
        File normOutputFile = new File(normOutputFileName);
        File optOutputFile = new File(optOutputFileName);
        PrintStream original = System.out;
        System.out.println("Begin testing");
        int N = 1;

        for (int i = 0; i < N; ++i) {
            generateCases();

            System.setOut(new PrintStream(optOutputFileName));
            long startTime = System.nanoTime();
            BattleRoyaleOptimized.main(null);
            long endTime = System.nanoTime();
            System.setOut(original);
            System.out.println("Optimized approach took " + ((endTime - startTime)/1000000) + " ms");

            System.setOut(new PrintStream(normOutputFileName));
            startTime = System.nanoTime();
            BattleRoyale.main(null);
            endTime = System.nanoTime();
            System.setOut(original);
            System.out.println("Norm approach took " + ((endTime - startTime)/1000000) + " ms");

            Scanner norm = new Scanner(normOutputFile);
            Scanner opt = new Scanner(optOutputFile);

            String normAns = "";
            while (norm.hasNextLine()) {
                normAns += norm.nextLine() + "\n";
            }

            String optAns = "";
            while (opt.hasNextLine()) {
                optAns += opt.nextLine() + "\n";
            }

            System.out.println(normAns.equals(optAns));

            norm.close();
            opt.close();

            if (!normAns.equals(optAns)) {
                break;
            }
        }
    }

    static int generateCases() throws Exception {
        final int MIN_MAP_SIZE = 15;
        final int MAX_MAP_SIZE = 15;
        final int LOOT_CHANCE = 10;
        File mapFile = new File("map.txt");
        PrintWriter writer = new PrintWriter(mapFile);
        Random rng = new Random();

        for (int i = MIN_MAP_SIZE; i <= MAX_MAP_SIZE; i += 2) {
            String[][] grid = new String[i][i];
            int lootCount = 0;
            for (int j = 0; j < i; ++j) {
                for (int k = 0; k < i; ++k) {
                    if (rng.nextInt(101) <= LOOT_CHANCE && lootCount <= (int)(i*i*LOOT_CHANCE)) {
                        grid[j][k] = Integer.toString(1);
//                        grid[j][k] = Integer.toString(rng.nextInt(9) + 1);
                        ++lootCount;
                    } else {
                        grid[j][k] = ".";
                    }
                }
            }
            int randRow = rng.nextInt(i);
            int randCol = rng.nextInt(i);
            grid[randRow][randCol] = "P";

            for (String[] strings : grid) {
                for (String string : strings) {
                    writer.print(string);
                }
                writer.println();
            }
            writer.println();
        }

        writer.close();
        return (MAX_MAP_SIZE - MIN_MAP_SIZE) / 2 + 1;
    }
}
