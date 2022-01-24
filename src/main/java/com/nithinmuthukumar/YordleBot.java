package com.nithinmuthukumar;

import com.mongodb.client.*;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.Embed;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.print.Doc;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;


public class YordleBot {
    public static void main(String[] args) {
        // Replace the uri string with your MongoDB deployment's connection string
        String uri = "mongodb://localhost:27017";
        MongoClient mongoClient = MongoClients.create(uri);
        MongoDatabase database = mongoClient.getDatabase("yordlebot");
        MongoCollection<Document> userCollection = database.getCollection("users");
        MongoCollection<Document> matchCollection = database.getCollection("matches");
        MongoCollection<Document> serverCollection = database.getCollection("servers");
//        Document doc = collection.find(eq("username", "popoplolj")).first();






        GatewayDiscordClient client = setupClient();
        Flux.interval(Duration.ofMinutes(10))
                .flatMap(ignore->{
                    updateData(client,userCollection,matchCollection,serverCollection);
                    return Mono.empty();

                }).subscribe();


        client.on(ChatInputInteractionEvent.class, event -> {



            try {
                String mode;
                List<Document> rankedUsers;
                Map<Document, Map<String,Double>> stats;
                EmbedCreateSpec.Builder embed;


                switch (event.getCommandName()) {
                    case "addplayer":

                        event.deferReply().block();

                        String username = event.getOption("username")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).get();
                        Snowflake snowflake = event.getOption("discorduser").flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asSnowflake).get();
                        Document summoner = RequestHandler.getSummoner(username).block();
                        ArrayList<String> matchIds = RequestHandler.getMatches(200, summoner.get("puuid").toString()).block();
                        for (String matchId : matchIds) {

                            if (matchCollection.countDocuments(eq("match_id", matchId)) == 0) {
                                matchCollection.insertOne(RequestHandler.getMatchData(matchId).block());


                            }

                        }
                        userCollection.insertOne(new Document("username", username)
                                .append("discord_user", snowflake.asString())
                                .append("match_history", matchIds)
                                .append("puuid", summoner.getString("puuid")));

//


                        return event.editReply(String.format("Username was added to leaderboard <@%s> ", snowflake.asString())).then();
                    case "games":
                        event.deferReply().block();

                        updateData(client,userCollection,matchCollection,serverCollection);
                        mode = event.getOption("mode")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).get();
                        stats = new HashMap<>();

                        for(Document user : userCollection.find()){
                            stats.put(user,getStats(1000,user,mode,matchCollection));

                        }
                        rankedUsers = stats.keySet().stream()
                                .sorted(Comparator.comparingDouble(u-> -stats.get(u)
                                        .get("games"))).collect(Collectors.toList());
                        embed = EmbedCreateSpec.builder()
                                .color(Color.GRAY)
                                .title("Leaderboard")
                                .description(String.format("Games Played"))

                                //.addAllFields(users)
                                .timestamp(Instant.now());

                        for (int i =0;i<rankedUsers.size();i++) {
                            Document user = rankedUsers.get(i);
                            double val=0;

                            embed.addField(String.format("%d. %s: %.0f",i+1,user.getString("username"),
                                            stats.get(user).get("games")),
                                    "\u200b", false);



                        }
                        return event.editReply().withEmbeds(embed.build()).then();



                    case "leaderboard":
                        event.deferReply().block();
                        updateData(client, userCollection, matchCollection, serverCollection);
                        mode = event.getOption("mode")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).get();

                        Double amount = Double.parseDouble(event.getOption("amount")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::getRaw).orElse("-1"));
                        String sortBy = event.getOption("sortby")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).orElse("placement");
                        stats = new HashMap<>();

                        for(Document user : userCollection.find()){
                            stats.put(user,getStats(amount,user,mode,matchCollection));

                        }
                        rankedUsers = stats.keySet().stream().sorted(Comparator.comparingDouble(u->{
                            switch (sortBy){
                                case "placement":
                                    return stats.get(u).get("placement")/stats.get(u).get("games");

                                case "wins":
                                    return -stats.get(u).get("wins")/stats.get(u).get("games");


                            }
                            return -1;


                        })).collect(Collectors.toList());








                        embed = EmbedCreateSpec.builder()
                                .color(Color.GRAY)
                                .title("Leaderboard")
                                .description(String.format("Last %d\n%s",amount.intValue(),sortBy))

                                //.addAllFields(users)
                                .timestamp(Instant.now());

                        for (int i =0;i<rankedUsers.size();i++) {
                            Document user = rankedUsers.get(i);
                            double val=0;
                            if(sortBy.equals("placement")){
                                val = stats.get(user).get("placement")/stats.get(user).get("games");



                            }
                            else if(sortBy.equals("wins")){
                                val = stats.get(user).get("wins");
                            }
                            embed.addField(String.format("%d. %s: %.2f",i+1,user.getString("username"),
                                            val),
                                    "\u200b", false);



                        }



                        return event.editReply().withEmbeds(embed.build()).then();





                    case "yordleversion":
                        try {
                            EmbedCreateSpec versionEmbed = EmbedCreateSpec.builder()
                                    .color(Color.GRAY)
                                    .title("Yordlebot")
                                    .description("Discord bot that provides information on tft")
                                    .addField("Version", "0.0.0", false)
                                    .addField("Last Updated", "06/25/02", false)
                                    .addField("Nithin Muthukumar", "https://www.nithinmuthukumar.com/", false)
                                    .timestamp(Instant.now())
                                    .footer(event.getCommandId().toString(), "")
                                    .build();


                            return event.reply().withEmbeds(versionEmbed);


                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case "registerchannel":
                        Snowflake channel = event.getOption("channel").flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asSnowflake).get();
                        serverCollection.insertOne(new Document("guild_id", event.getInteraction().getGuildId().get().asString()).append("channel_id", channel.asString()));
                        event.reply("channel do be added tho");

                }
            }catch (Exception e){
                e.printStackTrace();
            }
            return Mono.empty();


        }).subscribe();
        client.onDisconnect().block();

    }
    public static GatewayDiscordClient setupClient(){

        final GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("discordToken")).build()
                .login()
                .block();
        try {
            new GlobalCommandRegistrar(client.getRestClient()).registerCommands();
        } catch (Exception e) {
            //Handle exception
        }

        client.getEventDispatcher().on(ReadyEvent.class)
                .flatMap(ignored -> client.getChannelById(Snowflake.of("870826336715964419")))
                .ofType(MessageChannel.class)
                .flatMap((messageChannel)->{


                    return messageChannel.createMessage("Cocbot is online!");

                })
                .subscribe();

        return client;

    }

    public static void updateData(GatewayDiscordClient client, MongoCollection<Document> userCollection,MongoCollection<Document> matchCollection,MongoCollection<Document> serverCollection){

        try{
            for(Document user:userCollection.find()) {

                RequestHandler.getMatches(200, user.getString("puuid"))
                    .map(recentMatches->{

                        List<String> matchHistory = user.getList("match_history", String.class);
                        for (String matchId : recentMatches) {
                            if (!matchHistory.contains(matchId)) {
                                matchHistory.add(matchId);


                            }
                            if (matchCollection.countDocuments(eq("match_id", matchId)) == 0) {
                                RequestHandler.getMatchData(matchId).map(matchData -> matchCollection.insertOne(matchData)).delayElement(Duration.ofMillis(2000)).subscribe();


                                for (Document server : serverCollection.find()) {
                                    //TODO ucomment
                                    client.getChannelById(Snowflake.of(server.getString("channel_id"))).ofType(MessageChannel.class)
                                            .flatMap(messageChannel -> {

                                                return messageChannel.createMessage("a game with "+user.getString("username")+" has concluded");
                                            }).subscribe();


                                }
                            }
                        }

                        matchHistory.sort(Comparator.comparingLong(m -> {
                            try{

                                return matchCollection.find(eq("match_id", m))
                                        .first().get("info", Document.class)
                                        .getLong("game_datetime");

                            }catch (Exception e){

                                e.printStackTrace();

                            }
                            return 1;


                        }).reversed());

                        Bson updateOp = Updates.set("match_history", matchHistory);


                        UpdateResult result = userCollection.updateOne(eq("username", user.getString("username")), updateOp);
                        return Mono.empty();



                    }
                    ).subscribe();




            }


        }catch (Exception e){
            e.printStackTrace();
        }

    }
    public static Map<String,Double> getStats(double amount, Document user, String gameMode, MongoCollection<Document> matchCollection) {
        Map<String,Double> stats = new HashMap<>();
//        List<String> matches = user.getList("match_history",String.class).subList(0,(int)amount);
        List<String> matches = user.getList("match_history",String.class);
        double placementTotal=0;
        double firsts = 0;
        double games = 0;

        for(String matchId: matches){
            Document data = matchCollection.find(eq("match_id",matchId)).first();
            if(amount==0){
                break;
            }

            Document info = data.get("info",Document.class);
            String gameType = info.getString("tft_game_type");
            if(gameType==null||(!gameMode.equals("all")&&!gameType.equals(gameMode))){
                continue;
            }


            for(Document participant:info.getList("participants",Document.class)){
                if(participant.getString("puuid").equals(user.getString("puuid"))){

                    int placement=participant.getInteger("placement");
                    if(placement==1){
                        firsts++;
                    }
                    games+=1;

                    placementTotal+=placement;






                }
            }
            amount-=1;

        }
        stats.put("placement",placementTotal);
        stats.put("wins",firsts);
        stats.put("games",games);
        return stats;

    }
}
