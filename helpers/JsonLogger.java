package helpers;


import com.google.gson.Gson;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class JsonLogger {
    private static final String LOG_FILE = "server_logs.json";
    private static final Gson gson = new Gson();

    public static void log(String level, String message) {
        //create a log entry
        LogEntry logEntry = new LogEntry(level, message);

        //write the log entry to the file - logfile is automatically created
        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            gson.toJson(logEntry, writer);
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //inner class to represent a log entry
    private static class LogEntry {
        private final String timestamp;
        private final String level;
        private final String message;

        public LogEntry(String level, String message) {
            this.timestamp = LocalDateTime.now().toString();
            this.level = level;
            this.message = message;
        }
    }
}
