package co.purevanilla.mcplugins.kungfu;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class API {

    private boolean flushing;
    private final File dataFile;
    private final FileConfiguration data;
    private java.io.Writer chatOutput;
    private File chat;
    private final Plugin plugin;

    API(Plugin plugin) throws IOException, InvalidConfigurationException {
        flushing=false;

        this.plugin=plugin;
        this.plugin.saveDefaultConfig();

        // load the data index
        this.dataFile = this.createFile("data.yml", true);
        this.data = new YamlConfiguration();
        this.data.load(this.dataFile);
        if(this.data.getLong("block")==0){
            this.resetData();
        }

        // load the chat log
        this.chat = this.createFile("chat.log", false);
        if(Files.lines(this.chat.getAbsoluteFile().toPath()).count()!=data.getLong("lines")){
            this.resetData();
        }
        this.chatOutput = Files.newBufferedWriter(this.chat.toPath(), StandardOpenOption.APPEND);
    }


    /**
     * pushes chat message, and generates summary if needed
     */
    public void log(String sender, String message, @Nullable String recipient) throws IOException {
        // messages appended while flushing are ignored, in the future, I might add a pool
        if(flushing) return;

        String format = sender + ": ";
        if(recipient!=null){
            format+="@"+recipient+" ";
        }
        format += message;
        this.chatOutput.write(format+"\n");
        this.chatOutput.flush();
        this.data.set("lines", this.data.getLong("lines")+1);
        this.data.set("words", this.data.getLong("words")+format.split("\\s+").length);

        this.checkFlush();

    }

    public void checkFlush(){
        long lines = this.data.getLong("lines");
        long words = this.data.getLong("words");

        if(lines>=this.plugin.getConfig().getLong("limits.minimum")){
            long wordDelta = this.plugin.getConfig().getLong("limits.words");
            Long timestamp = this.currentTimestamp();
            long deltaBlock = timestamp-this.data.getLong("block");

            if(words>=wordDelta){
                this.plugin.getLogger().info("reached the word limit ("+words+")");
                this.flush(timestamp);
            } else if(deltaBlock>=this.plugin.getConfig().getLong("limits.timeframe")) {
                this.plugin.getLogger().info("reached the timeframe limit ("+deltaBlock+")");
                this.flush(timestamp);
            }
        }
    }

    /**
     * generates summary. can print stacktrace, uses global try-catch to prevent disrupting async operation
     */
    private void flush(Long finish) {
        flushing=true;
        Long start = this.data.getLong("block");
        try {
            final String content = Files.readString(this.chat.toPath());
            flushing=true;
            @NonNull ConfigurationSection promptSection = Objects.requireNonNull(this.plugin.getConfig().getConfigurationSection("prompt"));
            Set<String> prompts = promptSection.getKeys(false);
            for (String prompt: prompts) {
                if(!promptSection.getBoolean(prompt+".enabled")) continue;
                try {
                    this.generateReport(prompt, getOutput(prompt,content), start, finish);
                } catch (InterruptedException err){
                    err.printStackTrace();
                    this.plugin.getLogger().warning("unable to flush for prompt "+prompt);
                }
            }

            this.resetData(finish);
        } catch (IOException err){
            err.printStackTrace();
            this.plugin.getLogger().severe("unable to read or reset data index");
        }
        flushing=false;
    }

    private void close() throws IOException {
        this.data.save(this.dataFile);
        this.chatOutput.close();
    }

    /**
     * generates a report and formats it into the data.yml file, ready for moderators to
     * interact with using the UI and the server commands
     */
    private void generateReport(String prompt, String result, Long blockStart, Long blockFinish) throws IOException {
        // TODO
        this.plugin.getLogger().info("generated report for chat log started @ " + blockStart.toString() + " spanning until " + blockFinish.toString() + ", using the prompt "+prompt);
        this.plugin.getLogger().info(result);
        data.set("report."+blockStart.toString()+".until", blockFinish);
        data.set("report."+blockStart.toString()+".reviewed", new ArrayList<String>());
        data.set("report."+blockStart.toString()+".prompts."+prompt, result);
        data.save(this.dataFile);
    }

    /**
     * get output for a particular prompt
     */
    private String getOutput(String promptId, String content) throws IOException, InterruptedException {

        // https://platform.openai.com/docs/api-reference/chat/create

        JsonArray messages = new JsonArray();
        messages.add(this.getMessage("user",
            this.plugin.getConfig().getString("prompt."+promptId+".before") + "\n" +
                    content + "\n" +
                    this.plugin.getConfig().getString("prompt."+promptId+".after")
        ));

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model", "gpt-3.5-turbo");
        jsonObject.add("messages", messages);

        String body = new Gson().toJson(jsonObject);
        URI uri = URI.create("https://api.openai.com/v1/chat/completions");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.plugin.getConfig().getString("key"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject data = new Gson().fromJson(response.body(), JsonObject.class);

        JsonArray choices = data.getAsJsonArray("choices");
        StringBuilder cumulative = new StringBuilder();
        for (int i = 0; i < choices.size(); i++) {
            cumulative.append(choices.get(i).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString()).append("\n");
        }

        return cumulative.toString();
    }

    private JsonObject getMessage(String role, String content){
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private void resetData() throws IOException {
        this.resetData(this.currentTimestamp());
    }

    private long currentTimestamp(){
        return (long) Math.floor((double) System.currentTimeMillis() /1000);
    }

    private void resetData(long nextBlock) throws IOException {
        this.data.set("lines",0);
        this.data.set("words",0);
        this.data.set("block", nextBlock);
        if(this.chat!=null) this.chat.delete();
        this.chat = this.createFile("chat.log", false);
        this.chatOutput = Files.newBufferedWriter(this.chat.toPath(), StandardOpenOption.APPEND);
    }

    private File createFile(String name, boolean saveResource) throws IOException {
        File file = new File(this.plugin.getDataFolder(), name);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if(saveResource) {
                this.plugin.saveResource(name, false);
            } else {
                file.createNewFile();
            }
        }
        return file;
    }

}
