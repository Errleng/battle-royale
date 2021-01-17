import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class BattleRoyaleOptimized {
    // CONSTANTS BEGIN

    // characters that represent the player
    static final char CHAR_PLAYER = 'p';
    static final char CHAR_PLAYER_CAPITAL = 'P';
    // character to mark the positions in the path
    static final char CHAR_VISITED = 'v';
    // character to mark the final position of the path
    static final char CHAR_FINISH = 'f';

    // CONSTANTS END

    // GLOBAL VARIABLES BEGIN

    // grid of map's tiles
    static char[][] mapGrid;
    // grid of map's loot
    static int[][] mapLoot;
    // map dimensions
    static int mapLength, mapWidth;
    // maximum number of times the boundaries can shrink without covering the centre
    static int maxDepth;
    // coordinates of the map's centre
    static int centreRow, centreCol;
    // character which represents the player, the same as the input map
    static char mainCharPlayer = CHAR_PLAYER;

    // AStarSearcher uses A* algorithm to search for paths
    static AStarSearcher pathfinder;

    // For dynamic programming
    // Zobrist uses Zobrist hashing to generate keys for dynamic programming states
    static ZobristHasher zob;
    // Hashmap that stores solutions to states
    static HashMap<Long, List<Position>> storedSolutions;

    // GLOBAL VARIABLES END

    /**
     * main
     * The main method of the program
     *
     * @param args Possible command line arguments
     * @throws IOException Throws this exception if the map file is not found
     */
    public static void main(String[] args) throws IOException {
        Scanner userInput = new Scanner(System.in);

        // get the name of the file that contains the map

        do {
            // get the map file
            System.out.println("Enter the name of the file containing the map");
//            String mapFileName = userInput.nextLine();
            String mapFileName = "map.txt";
            File mapFile = new File(mapFileName);

            // read the map from the file
            if (mapFile.exists() && mapFile.isFile()) {
                mapGrid = getMapFromFile(mapFile);
                if (mapGrid == null) {
                    System.out.println("The file does not contain a map");
                }
            } else {
                System.out.println("The file does not exist");
            }
        } while (mapGrid == null);

        // sets global variables about map attributes
        // the map dimensions
        mapLength = mapGrid.length;
        mapWidth = mapGrid[0].length;

        // the row and column of the map centre
        centreRow = mapLength / 2;
        centreCol = mapWidth / 2;

        // the maximum amount of times the boundaries can shrink without covering the centre
        maxDepth = Math.min(mapLength / 2, mapWidth / 2);

        // fill the loot grid with the amounts of loot
        mapLoot = new int[mapLength][mapWidth];
        for (int i = 0; i < mapLength; ++i) {
            for (int j = 0; j < mapWidth; ++j) {
                if (Character.isDigit(mapGrid[i][j])) {
                    mapLoot[i][j] = Character.getNumericValue(mapGrid[i][j]);
                }
            }
        }

        // initialize path finding objects
        pathfinder = new AStarSearcher(copyArray(mapGrid));

        // initialize dynamic programming objects
        storedSolutions = new HashMap<>();
        zob = new ZobristHasher(mapLength * mapWidth, mapLength * mapWidth);

        // the row and column to start searching from. set to -1 by default
        int startRow = -1;
        int startCol = -1;

        // the best path found
        char[][] bestPathMap = null;
        // the amount of loot the best path contains
        int bestLoot = -1;

        // finds the player's position in the map, if it exists
        for (int i = 0; i < mapLength; ++i) {
            for (int j = 0; j < mapWidth; ++j) {
                if (mapGrid[i][j] == CHAR_PLAYER || mapGrid[i][j] == CHAR_PLAYER_CAPITAL) {
                    startRow = i;
                    startCol = j;
                    mainCharPlayer = mapGrid[i][j];
                }
            }
        }

        long startTime = System.nanoTime();

        // if the player is found, find the best path from the player's position
        if ((startRow != -1) && (startCol != -1)) {
            bestPathMap = findBestPath(mapGrid, startRow, startCol);
            if (bestPathMap != null) {
                bestLoot = getPathMapLoot(bestPathMap);
            }
        } else {
            // if the player is not found, find the best location to drop in
            // iterate over all possible positions and try starting from them
            for (int i = 0; i < mapGrid.length; ++i) {
                for (int j = 0; j < mapGrid[i].length; ++j) {
                    // check if the position is valid to drop in
                    if ((mapLoot[i][j] == 0) && (!shouldJustDie(i, j, 0))) {
                        // finds a path using i and j as the position
                        char[][] potentialSolution = findBestPath(mapGrid, i, j);
                        if (potentialSolution != null) {
                            int potentialLoot = getPathMapLoot(potentialSolution);
                            // sets the best path to this path if its loot is higher than the best loot so far
                            if (potentialLoot > bestLoot) {
                                bestLoot = potentialLoot;
                                bestPathMap = potentialSolution;
                                startRow = i;
                                startCol = j;
                            }
                        }
                    }
                }
            }
        }

        long endTime = System.nanoTime();
        System.out.println("It took " + ((endTime - startTime) / 1000000) + " milliseconds to find the best path");

        // best path will be null if no path was found
        if (bestPathMap != null) {
            System.out.println("Path from (" + startRow + "," + startCol + ") to (" + centreRow + "," + centreCol + ") with " + bestLoot + " item(s) looted");
            printArray(bestPathMap);
        } else {
            System.out.println("No path exists");
        }

        userInput.close();
    }

    /**
     * findBestPath
     * Finds and returns the path that contains the highest amount of loot and ends in the centre without dying.
     *
     * @param originalMap A version of the map that will be used as a reference.
     * @param startRow    The row to start from.
     * @param startCol    The column to start from.
     * @return char[][] a 2D array of characters representing a map with a path drawn on it. Will be null if there is no valid path.
     */
    static char[][] findBestPath(char[][] originalMap, int startRow, int startCol) {
        storedSolutions.clear();

        List<Position> targets = getValidTargets(startRow, startCol, 0);
        int[] targeted = new int[targets.size() + 1];
        List<Position> path = findBestPathHelper(targets, targeted, startRow, startCol, startRow, startCol, new ArrayList<>());

        if (path != null) {
            char[][] solution = copyArray(originalMap);
            for (Position position : path) {
                solution[position.row][position.col] = CHAR_VISITED;
            }
            solution[startRow][startCol] = mainCharPlayer;
            if (path.size() > 0) {
                Position last = path.get(path.size() - 1);
                solution[last.row][last.col] = CHAR_FINISH;
            }
            return solution;
        } else {
            return null;
        }
    }

    /**
     * findBestPathHelper
     *
     * @param targets A list of positions of loot.
     * @param state   An int array representing the current state.
     *                The first index holds the state's depth.
     *                The following indexes hold 0 if the target at the same index has not been targeted, or 1 if it has.
     * @param row     The current row.
     * @param col     The current column.
     * @param goalRow The row to end the path on.
     * @param goalCol The column to end the path on.
     * @param path    The path with the highest amount of loot without the player dying.
     * @return Map with the path with the highest amount of loot without the player dying.
     */
    static List<Position> findBestPathHelper(List<Position> targets, int[] state, int row, int col, int goalRow, int goalCol, List<Position> path) {
        // the dynamic programming state is what targets have been visited or not
        // the state hash is therefore created from the targeted array
        long targetedKey = zob.hash(state);

        // if the state has been solved before, return the stored solution
        if (storedSolutions.containsKey(targetedKey)) {
            return storedSolutions.get(targetedKey);
        }
        // current depth is stored in the first index of the state array
        // depth is the amount of times the boundaries have shrunk
        int depth = state[0];

        // if the the target has been reached
        if ((row == goalRow) && (col == goalCol)) {
            // if the target was the centre, return a potential path
            if ((goalRow == centreRow) && (goalCol == centreCol)) {
                return new ArrayList<>(path);
            }

            int bestLoot = -1;
            List<Position> bestPath = null;

            // iterate over all targets and target those not yet targeted
            for (int i = 0; i < targets.size(); ++i) {
                if (state[i + 1] == 0) {
                    Position targetPos = targets.get(i);
                    int targetLoot = mapLoot[targetPos.row][targetPos.col];

                    int nextDepth = depth;
                    boolean takesTimeToLoot = (targetLoot > 1);
                    if (takesTimeToLoot) {
                        // the increase in depth is the length of the path to the target
                        // however, the target already takes time to loot and it is inside the path to it
                        // avoid counting both its loot and its existence in the path
                        nextDepth += targetLoot - 1;
                    }

                    // if the player can loot the target and live
                    if (canLootAndLive(row, col, targetPos.row, targetPos.col, nextDepth)) {
                        // mark this target as having been targeted
                        state[i + 1] = 1;
                        // increase depth by the time it takes to loot the target
                        state[0] = nextDepth;

                        // set a new target and get the resulting path
                        List<Position> finalPath = findBestPathHelper(targets, state, row, col, targetPos.row, targetPos.col, path);

                        // check if a possible path even exists
                        if (finalPath != null) {
                            int finalLoot = getPathLoot(finalPath);
                            // updates the best path if the potential path's loot is higher than the best loot so far
                            if (finalLoot > bestLoot) {
                                bestLoot = finalLoot;
                                bestPath = finalPath;
                            }
                        }

                        // undo marking this target as having been targeted
                        // this avoids having to copy the array to ensure correctness
                        state[i + 1] = 0;

                        // undo depth increase by the time it takes to loot the target
                        state[0] = depth;
                    }
                }
            }

            // store the solution for this state and return the solution
            // checks if the solution is already stored, which should theoretically never be the case
            if (!storedSolutions.containsKey(targetedKey)) {
                storedSolutions.put(targetedKey, bestPath);
            }
            return bestPath;
        } else {
            // the current target has not been reached

            // find a path segment from the current position to the target
            List<Position> lootPath = pathfinder.findPath(row, col, goalRow, goalCol);

            if (lootPath != null) {
                // the path contains the current position, so remove it as it is unnecessary
                lootPath.remove(lootPath.size() - 1);

                // the next depth is the current depth plus the number of positions in the path
                int nextDepth = depth + lootPath.size();

                // the boundaries have shrunk too much and there is no spot out of bounds
                if (nextDepth > maxDepth) {
                    return null;
                }

                // add the path segment to the entire path
                for (int i = lootPath.size() - 1; i >= 0; i--) {
                    Position pos = lootPath.get(i);
                    path.add(pos);
                }

                // update the state's depth
                state[0] = nextDepth;

                // get the position the player ends up on after taking the path segment
                Position endingPos = lootPath.get(0);
                List<Position> result = findBestPathHelper(targets, state, endingPos.row, endingPos.col, goalRow, goalCol, path);

                // undo updating the state's depth
                state[0] = depth;

                // remove the path segment from the entire path
                for (int i = 0; i < lootPath.size(); ++i) {
                    path.remove(path.size() - 1);
                }
                return result;
            } else {
                // no path was found
                return null;
            }
        }
    }

    /**
     * getValidTargets
     * Gets the positions of loot which the player can loot without dying
     *
     * @param row   The row the player is on
     * @param col   The column the player is on
     * @param depth The number of times the boundaries have shrunk
     * @return A list of positions of loot that the player can loot without dying
     */
    static List<Position> getValidTargets(int row, int col, int depth) {
        List<Position> targets = new ArrayList<>();
        // the centre is always a possible target
        targets.add(new Position(centreRow, centreCol));

        // iterate over the map and add positions containing loot
        for (int i = 0; i < mapLength; ++i) {
            for (int j = 0; j < mapWidth; ++j) {
                if (mapLoot[i][j] > 0) {
                    // the time it takes to loot
                    int lootTime = 0;
                    if (mapLoot[i][j] > 1) {
                        lootTime = mapLoot[i][j];
                    }
                    if (canLootAndLive(row, col, i, j, depth + lootTime)) {
                        targets.add(new Position(i, j));
                    }
                }
            }
        }
        return targets;
    }

    /**
     * canLootAndLive
     * Checks if the player can reach the target position and make it to the centre without dying
     *
     * @param row     The row of the player's position
     * @param col     The column of the player's position
     * @param goalRow The row of the target's position
     * @param goalCol The column of the target's position
     * @param depth   The number of times the boundaries have shrunk
     * @return boolean, True if the player can reach the target position and go to the centre safely
     */
    static boolean canLootAndLive(int row, int col, int goalRow, int goalCol, int depth) {
        int goalRowDist = Math.abs(row - goalRow);
        int goalColDist = Math.abs(col - goalCol);

        depth += (goalRowDist + goalColDist);

        return !shouldJustDie(goalRow, goalCol, depth);
    }

    /**
     * shouldJustDie
     * Checks if the player cannot outrun the boundaries to the centre and will die.
     *
     * @param playerRow The row the player is on.
     * @param playerCol The column the player is on.
     * @param depth     The number of times the boundaries have shrunk.
     * @return boolean, True if the player will die from the boundaries while trying to reach the centre.
     */
    static boolean shouldJustDie(int playerRow, int playerCol, int depth) {
        // the row that the bottom boundary is on
        int boundBottom = mapLength - depth - 1;
        // the column that the right boundary is on
        int boundRight = mapWidth - depth - 1;

        // the distances to the centre row and column
        int endRowDist = Math.abs(playerRow - centreRow);
        int endColDist = Math.abs(playerCol - centreCol);

        // the player's distance from the top boundary
        int boundTopDist = playerRow - depth;
        // the player's distance from the bottom boundary
        int boundBottomDist = boundBottom - playerRow;
        // the player's distance from the left boundary
        int boundLeftDist = playerCol - depth;
        // the player's distance from the right boundary
        int boundRightDist = boundRight - playerCol;

        // checks if it takes more time for the player to reach the centre than for the perpendicular boundaries to close in
        return (endRowDist > boundLeftDist) || (endRowDist > boundRightDist) || (endColDist > boundTopDist) || (endColDist > boundBottomDist);
    }

    /**
     * getPathMapLoot
     * Gets the amount of loot a map with a path contains.
     *
     * @param map A map with a path drawn on it.
     * @return int The amount of loot the path contains
     */
    static int getPathMapLoot(char[][] map) {
        int totalLoot = 0;
        // iterates over all loot positions and sums the loot
        for (int i = 0; i < mapLength; ++i) {
            for (int j = 0; j < mapWidth; ++j) {
                if (map[i][j] == CHAR_VISITED || map[i][j] == CHAR_FINISH) {
                    totalLoot += mapLoot[i][j];
                }
            }
        }
        return totalLoot;
    }

    /**
     * getPathLoot
     * Gets the amount of loot a path contains.
     *
     * @param path A list of positions representing the map
     * @return int The amount of loot the path contains
     */
    static int getPathLoot(List<Position> path) {
        // keeps track if the position has already been looted
        boolean[][] looted = new boolean[mapLength][mapWidth];
        int totalLoot = 0;
        // iterates over the path's positions and sums the loot
        for (Position position : path) {
            if (!looted[position.row][position.col]) {
                looted[position.row][position.col] = true;
                totalLoot += mapLoot[position.row][position.col];
            }
        }
        return totalLoot;
    }

    /**
     * getMapFromFile
     * Reads a map from a file.
     *
     * @param mapFile The file to read.
     * @return A 2D character array representing the map.
     * @throws IOException Throws this exception if the file is not found.
     */
    static char[][] getMapFromFile(File mapFile) throws IOException {
        Scanner fileInput = new Scanner(mapFile);

        if (fileInput.hasNextLine()) {
            // the current line read from the file
            String line = fileInput.nextLine().replace(" ", "");
            // the map in string form
            String mapString = "";

            // the number of rows of the map
            int rows = 0;
            // the number of columns in the map
            int columns = line.length();

            // continues reading map as long as there are more lines and the line is not blank
            while (!line.isEmpty()) {
                mapString += line;
                ++rows;
                if (fileInput.hasNextLine()) {
                    line = fileInput.nextLine().replace(" ", "");
                } else {
                    line = "";
                }
            }

            char[][] map = new char[rows][columns];
            // fill the char[][] map using the string representation of the map
            for (int i = 0; i < rows; ++i) {
                for (int j = 0; j < columns; ++j) {
                    // transforms 2D indexes to 1D using 'number of rows' * 'length of a row' + 'number of columns'
                    // 'i' is the number of rows and 'j' is the number of columns
                    map[i][j] = mapString.charAt(i * columns + j);
                }
            }

            fileInput.close();
            return map;
        } else {
            fileInput.close();
            return null;
        }
    }

    /**
     * copyMap
     * Copies and returns the given char[][].
     *
     * @param array A 2D char array.
     * @return char[][] A 2D char array that is a copy of the input.
     */
    static char[][] copyArray(char[][] array) {
        char[][] copy = new char[array.length][array[0].length];
        for (int i = 0; i < array.length; ++i) {
            for (int j = 0; j < array[0].length; ++j) {
                copy[i][j] = array[i][j];
            }
        }
        return copy;
    }

    /**
     * printArray
     * Outputs the given char[][]
     *
     * @param array A 2D char array
     */
    static void printArray(char[][] array) {
        for (int i = 0; i < array.length; ++i) {
            for (int j = 0; j < array[0].length; ++j) {
                System.out.print(array[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }
}
