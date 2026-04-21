package haruhikage;

import carpet.api.settings.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HaruhikageAddonSettings {
    public static final Logger LOGGER = LogManager.getLogger("haruhikage-addon");
    public static final String fallingblock = "Haruhikage";

    @Rule(
            desc = "Log async beacom beam times in server console.",
            categories = {fallingblock},
            options = {"true", "false"}
    )
    public static boolean logAsyncTimes = false;

    @Rule(
            desc = "Logs 'Chunk Unload' phase and 'Player' phase in server console. Unload chunk unload will be displayed in chat",
            categories = {fallingblock},
            options = {"true", "false"}
    )
    public static boolean logCertainTickPhases = false;

    @Rule(
            desc = "Logs whenever an autosave induced by pressing ESC or writing a book happens in console",
            categories = {fallingblock},
            options = {"true", "false"}
    )
    public static boolean logAutosaveTime = false;

    @Rule(
            desc = "Unload Chunk X coordinate for the `logUnloadChunkPhase` logger",
            categories = {fallingblock},
            options = {"1", "2", "3"},
            strict = false
    )
    public static int unloadChunkX = 1;

    @Rule(
            desc = "Unload Chunk Z coordinate for the `logUnloadChunkPhase` logger",
            categories = {fallingblock},
            options = {"1", "2", "3"},
            strict = false
    )
    public static int unloadChunkZ = 1;

    @Rule(
            desc = "Logs population of certain chunks",
            categories = {fallingblock},
            options = {"true", "false"}
    )
    public static boolean logChunkPopulation = false;

    @Rule(
            desc = "Enables and tracks loading events of chunks using the /chunkTrack command in chat. Serves as an alternative to chunk debug without the need of external tools",
            categories = {fallingblock},
            options = {"true", "false"}
    )
    public static boolean chunkTrackCommand = false;

    @Rule(
        desc = "Enables the /palette command to debug the subchunk palette.",
        categories = {fallingblock},
        options = {"true", "false"}
    )
    public static boolean paletteCommand = false;

    @Rule(
            desc = "Disables terrain population. Useful when testing and interacting with contraptions with unpopulated chunks",
            categories = {fallingblock},
            options = {"true", "false"}
    )
    public static boolean disableTerrainPopulation = false;


}
