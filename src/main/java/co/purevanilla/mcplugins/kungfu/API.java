package co.purevanilla.mcplugins.kungfu;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import java.util.Objects;
import java.util.Set;

public class API {

    private boolean flushing;
    private final File dataFile;
    private final FileConfiguration data;
    private final java.io.Writer chatOutput;
    private final File chat;
    private final Plugin plugin;

    API(Plugin plugin) throws IOException, InvalidConfigurationException {
        flushing=false;

        this.plugin=plugin;

        // load the data index
        this.dataFile = this.createFile("data.yml", true);
        this.data = new YamlConfiguration();
        this.data.load(this.dataFile);
        if(this.data.getLong("block")==0){
            this.resetData();
        }

        // load the chat log
        final String chatLog = "chat.log";
        this.chat = this.createFile("chat.log", false);
        if(Files.lines(this.chat.getAbsoluteFile().toPath()).count()!=data.getLong("lines")){
            this.resetData();
        }
        this.chatOutput = new BufferedWriter(new FileWriter(this.chat, true));
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
        long words = this.data.getLong("words")+format.split("\\s+").length;
        this.chatOutput.append(format);
        this.data.set("lines", this.data.getLong("lines")+1);
        this.data.set("words", words);

        long deltaBlock = System.currentTimeMillis()-this.data.getLong("block")*1000;

        if(words>=this.plugin.getConfig().getLong("limits.words") || deltaBlock>=this.plugin.getConfig().getLong("limits.timeframe")){
            this.flush();
        }
    }

    /**
     * generates summary. can print stacktrace, uses global try-catch to prevent disrupting async operation
     */
    private void flush() {
        flushing=true;
        Long start = this.data.getLong("block");
        Long finish = this.currentTimestamp();
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

    /**
     * generates a report and formats it into the data.yml file, ready for moderators to
     * interact with using the UI and the server commands
     */
    private void generateReport(String prompt, String result, Long blockStart, Long blockFinish){
        // TODO
        this.plugin.getLogger().info("generated report for chat log started @ " + blockStart.toString() + " spanning until " + blockFinish.toString() + ", using the prompt "+prompt);
        this.plugin.getLogger().info(result);
    }

    /**
     * get output for a particular prompt
     */
    private String getOutput(String promptId, String content) throws IOException, InterruptedException {

        // https://platform.openai.com/docs/api-reference/chat/create

        JsonArray messages = new JsonArray();
        messages.add(this.getMessage("system","You are a helpful assistant."));
        messages.add(this.getMessage("user",
            this.plugin.getConfig().getString("prompt."+promptId+".before") + "\n" +
                    content + "\n" +
                    this.plugin.getConfig().getString("prompt."+promptId+".after")
        ));

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model", "text-davinci-003");
        jsonObject.add("messages", messages);

        URI uri = URI.create("https://api.openai.com/v1/chat/completions");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.plugin.getConfig().getString("key"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.getAsString()))
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

    private JsonPrimitive getMessage(String role, String content){
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message.getAsJsonPrimitive();
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
        this.chat.delete();
        this.createFile("chat.log", false);
    }

    private File createFile(String name, boolean saveResource) throws IOException {
        File file = new File(this.plugin.getDataFolder(), name);
        if (!this.dataFile.exists()) {
            if(!file.getParentFile().mkdirs()) throw new IOException("unable to create file");
            if(saveResource) this.plugin.saveResource(name, false);
        }
        return file;
    }

}
