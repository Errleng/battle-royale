import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * [BattleRoyale.java]
 * Simulates a battle royale game with only one player
 * Author: Eric Liang
 * April 4, 2019
 */

class BattleRoyale {
    // CONSTANTS BEGIN

    // characters that represent the player
    static final char CHAR_PLAYER = 'p';
    static final char CHAR_PLAYER_CAPITAL = 'P';
    // character to mark the positions in the path
    static final char CHAR_VISITED = 'v';
    // character to mark the final position of the path
    static final char CHAR_FINISH = 'f';
    // offsets to be added to a position to get the next position
    static final int[][] MOVES = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {0, 0}};

    // CONSTANTS END

    // GLOBAL VARIABLES BEGIN

    // 2D char array which contains the raw character of the inputted map at a position
    static char[][] mapGrid;
    // 2D int array which contains the amount of loot at a position
    static int[][] mapLoot;
    // character which represents the player, the same as the input map
    static char mainCharPlayer = CHAR_PLAYER;

    // GLOBAL VARIABLES END

    /**
     * main
     * This is the starting method of the program
     * @param args String array of command line arguments
     * @throws IOException Thrown if the map file is not found
     */
    public static void main(String[] args) throws IOException {
        Scanner userInput = new Scanner(System.in);

        do {
            // get the map file
            System.out.println("Enter the name of the file containing the map");
            String mapFileName = userInput.nextLine();
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

        int mapLength = mapGrid.length;
        int mapWidth = mapGrid[0].length;

        // fill the loot grid with the amounts of loot
        mapLoot = new int[mapLength][mapWidth];
        for (int i = 0; i < mapLength; ++i) {
            for (int j = 0; j < mapWidth; ++j) {
                if (Character.isDigit(mapGrid[i][j])) {
                    mapLoot[i][j] = Character.getNumericValue(mapGrid[i][j]);
                }
            }
        }

        // the row and column of the centre of the map
        int centreRow = mapLength/2;
        int centreCol = mapWidth/2;

        // the row and column to start searching from. set to -1 by default
        int startRow = -1;
        int startCol = -1;

        // the best path found
        char[][] bestPathMap = null;
        // the amount of loot the best path contains
        int bestLoot = -1;

        // finds the player's position in the map, if it exists
        for (int i = 0; i < mapGrid.length; ++i) {
            for (int j = 0; j < mapGrid[i].length; ++j) {
                if (mapGrid[i][j] == CHAR_PLAYER || mapGrid[i][j] == CHAR_PLAYER_CAPITAL) {
                    mainCharPlayer = mapGrid[i][j];
                    startRow = i;
                    startCol = j;
                }
            }
        }

        long startTime = System.nanoTime();

        // if the player is found, find the best path from the player's position
        if ((startRow != -1) && (startCol != -1)) {
            bestPathMap = findBestPath(mapGrid, startRow, startCol, centreRow, centreCol);
            if (bestPathMap != null) {
                bestLoot = getPathMapLoot(bestPathMap);
            }
        } else {
            // if the player is not found, find the best location to drop in
            // iterate over all possible positions and try starting from them
            for (int i = 0; i < mapGrid.length; ++i) {
                for (int j = 0; j < mapGrid[i].length; ++j) {
                    // check if there is no loot at the drop in position
                    if (mapLoot[i][j] == 0) {
                        // finds a path using i and j as the position
                        char[][] potentialSolution = findBestPath(mapGrid, i, j, centreRow, centreCol);
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
        System.out.println("It took " + ((endTime - startTime)/1000000) + " milliseconds to find the best path");

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
     * @param originalMap A version of the map that will be used as a reference.
     * @param startRow The row to start from.
     * @param startCol The column to start from.
     * @param goalRow The row to end the path on.
     * @param goalCol The column to end the path on.
     * @return char[][] a 2D array of characters representing a map with a path drawn on it. Will be null if there is no valid path.
     */
    static char[][] findBestPath(char[][] originalMap, int startRow, int startCol, int goalRow, int goalCol) {
        // copy of the map that will be modified to contain the path
        char[][] pathMap = copyArray(originalMap);
        // array that stores whether the position was looted before
        boolean[][] looted = new boolean[originalMap.length][originalMap[0].length];

        char[][] resultPath = findBestPathHelper(originalMap, pathMap, looted, startRow, startCol, goalRow, goalCol, 0);

        if (resultPath != null) {
            resultPath[startRow][startCol] = CHAR_PLAYER;
            resultPath[goalRow][goalCol] = CHAR_FINISH;
        }
        return resultPath;
    }

    /**
     * findBestPathHelper
     * Recursively explores neighboring positions and builds paths.
     * Will return a copy of the path if it reaches the centre and the boundaries have shrunk to the minimum size without killing the player.
     * @param originalMap A version of the map that will not be modified to be used as reference.
     * @param pathMap A version of the map that contains the path so far.
     * @param looted 2D array that stores whether the position was looted before
     * @param row The current row the player would be on.
     * @param col The current column the player would be on.
     * @param goalRow The row to the path end on.
     * @param goalCol The column to the path end on.
     * @param depth The number of times the boundaries have shrunk.
     * @return Map whe path with the highest amount of loot without the player dying.
     */
    static char[][] findBestPathHelper(char[][] originalMap, char[][] pathMap, boolean[][] looted, int row, int col, int goalRow, int goalCol, int depth) {
        int mapLength = originalMap.length;
        int mapWidth = originalMap[0].length;
        // the maximum number of times the boundaries can shrink without completey covering the map
        int maxDepth = Math.min(mapLength/2, mapWidth/2);
        // set this position to be on the path
        pathMap[row][col] = CHAR_VISITED;

        // returns a valid path if the position is equal to the goal and all moves have been used up
        if ((row == goalRow) && (col == goalCol) && (depth == maxDepth)) {
            return copyArray(pathMap);
        }

        int bestLoot = -1;
        char[][] bestPathMap = null;

        // tries all possible moves and finds the best path resulting from them
        for (int i = 0; i < MOVES.length; ++i) {
            int nextRow = row + MOVES[i][0];
            int nextCol = col + MOVES[i][1];

            if (isInBounds(originalMap.length, originalMap[0].length, nextRow, nextCol)) {
                boolean[][] nextLooted = copyArray(looted);

                int nextDepth = depth;

                // if the loot is greater than 1 and it has not been looted, loot it and shrink boundaries by the loot
                if ((mapLoot[nextRow][nextCol] > 1) && (!nextLooted[nextRow][nextCol])) {
                    nextLooted[nextRow][nextCol] = true;
                    // the next depth is the current depth plus the amount of time it takes to loot
                    nextDepth += mapLoot[nextRow][nextCol];
                } else {
                    // the next depth is the current depth plus one for the move
                    nextDepth += 1;
                }

                // checks if the player has died from the boundaries
                if (!dieFromBoundary(mapLength, mapWidth, nextRow, nextCol, nextDepth)) {
                    char[][] nextPathMap = copyArray(pathMap);
                    char[][] finalMap = findBestPathHelper(originalMap, nextPathMap, nextLooted, nextRow, nextCol, goalRow, goalCol, nextDepth);
                    if (finalMap != null) {
                        int finalLoot = getPathMapLoot(finalMap);
                        if (finalLoot > bestLoot) {
                            bestLoot = finalLoot;
                            bestPathMap = finalMap;
                        }
                    }
                }
            }
        }

        // best path will be null if there is no further possible path
        return bestPathMap;
    }

    /**
     * isInBounds
     * Checks if the given position is within the given dimensions.
     * @param mapLength The number of rows the map has.
     * @param mapWidth The number of columns the map has.
     * @param row The row of the position to consider.
     * @param col The column of the position to consider.
     * @return boolean True if the position is within the dimensions of the map, false otherwise.
     */
    static boolean isInBounds(int mapLength, int mapWidth, int row, int col) {
        return ((row >= 0) && (row < mapLength) && (col >= 0) && (col < mapWidth));
    }

    /**
     * dieFromBoundary
     * Checks if the player dies at the position from boundaries shrunk the given number of times.
     * @param mapLength The number of rows the map has.
     * @param mapWidth The number of columns the map has.
     * @param playerRow The row the player is on.
     * @param playerCol The column the player is on.
     * @param depth The number of times the boundaries have shrunk.
     * @return boolean True if the player dies from the boundaries, false otherwise
     */
    static boolean dieFromBoundary(int mapLength, int mapWidth, int playerRow, int playerCol, int depth) {
        // the row that the bottom boundary is on.
        int boundBottom = mapLength - depth - 1;
        // the column that the right boundary is on.
        int boundRight = mapWidth - depth - 1;
        // checks if the row is on or outside the boundary
        return (playerRow < depth) || (playerCol < depth) || (playerRow > boundBottom) || (playerCol > boundRight);
    }

    /**
     * getPathMapLoot
     * Finds and returns the amount of loot a map with a path contains.
     * @param pathMap A map with a path drawn on it.
     * @return int The amount of loot the path contains.
     */
    static int getPathMapLoot(char[][] pathMap) {
        int totalLoot = 0;
        // iterates over the entire path and sums the loot from positions in the path
        for (int i = 0; i < pathMap.length; ++i) {
            for (int j = 0; j < pathMap[i].length; ++j) {
                if (pathMap[i][j] == CHAR_VISITED || pathMap[i][j] == CHAR_FINISH) {
                    totalLoot += mapLoot[i][j];
                }
            }
        }
        return totalLoot;
    }

    /**
     * getMapFromFile
     * Reads a map from a file.
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
     * printArray
     * Outputs the given char[][]
     * @param array A 2D char array.
     */
    static void printArray(char[][] array) {
        for (int i = 0; i < array.length; ++i) {
            for (int j = 0; j < array[0].length; ++j) {
                System.out.print(Character.toString(array[i][j]));
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * copyArray
     * Copies and returns the given boolean[][]
     * @param array A 2D boolean array.
     * @return boolean[][] A 2D boolean array that is a copy of the input.
     */
    static boolean[][] copyArray(boolean[][] array) {
        boolean[][] copy = new boolean[array.length][array[0].length];
        for (int i = 0; i < array.length; ++i) {
            for (int j = 0; j < array[i].length; ++j) {
                copy[i][j] = array[i][j];
            }
        }
        return copy;
    }

    /**
     * copyArray
     * Copies and returns the given char[][]
     * @param array A 2D char array.
     * @return char[][] A 2D char array that is a copy of the input.
     */
    static char[][] copyArray(char[][] array) {
        char[][] copy = new char[array.length][array[0].length];
        for (int i = 0; i < array.length; ++i) {
            for (int j = 0; j < array[i].length; ++j) {
                copy[i][j] = array[i][j];
            }
        }
        return copy;
    }
}
