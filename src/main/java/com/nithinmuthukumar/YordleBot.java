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
                .flatMap(ignored -> client.getChannelById(Snowflake.of("888219141478187029")))
                .ofType(MessageChannel.class)
                .flatMap((messageChannel)->{


                    return messageChannel.createMessage("Cocbot is online!");

                })
                .subscribe();

        return client;

    }
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
                Map<Document, Map<String,Number>> stats;
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
                                .append("puuid", summoner.getString("puuid"))
                                .append("summonerId",summoner.getString("id")));


//


                        return event.editReply(String.format("Username was added to leaderboard <@%s> ", snowflake.asString())).then();
                    case "lastplayed":
                        event.deferReply().block();
                        updateData(client,userCollection,matchCollection,serverCollection);
                        mode = event.getOption("mode")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).get();
                        Double amount = Double.parseDouble(event.getOption("amount")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::getRaw).orElse("1"));
                        stats = new HashMap<>();
                        for(Document user: userCollection.find()){
                            stats.put(user,getStats(amount,user,mode,matchCollection));
                        }
                        embed = getLastPlayed(stats, mode,amount);
                        return event.editReply().withEmbeds(embed.build()).then();


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
                        embed = getGames(stats);
                        return event.editReply().withEmbeds(embed.build()).then();




                    case "leaderboard":
                        event.deferReply().block();
                        updateData(client, userCollection, matchCollection, serverCollection);
                        mode = event.getOption("mode")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).get();

                        amount = Double.parseDouble(event.getOption("amount")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::getRaw).orElse("-1"));
                        String sortBy = event.getOption("sortby")
                                .flatMap(ApplicationCommandInteractionOption::getValue)
                                .map(ApplicationCommandInteractionOptionValue::asString).orElse("placement");
                        stats = new HashMap<>();

                        for(Document user : userCollection.find()){
                            stats.put(user,getStats(amount,user,mode,matchCollection));

                        }
                        embed = getLeaderboard(stats, mode,amount, sortBy);


                        return event.editReply().withEmbeds(embed.build()).then();
                    case "ranks":
                        event.deferReply().block();
                        embed = EmbedCreateSpec.builder()
                                .color(Color.GRAY)
                                .title("Ranks")
                                //.addAllFields(users)
                                .timestamp(Instant.now());

                        for(Document user: userCollection.find()){
                            List<Document> ranks = RequestHandler.getRank(user.getString("summonerId")).block().getList("queues",Document.class);
			    System.out.println(ranks);
			    for(Document rankedQueue:ranks){
				    if(rankedQueue.getString("queueType").equals("RANKED_TFT")){
					    System.out.println(rankedQueue.getString("tier"));
					    embed.addField(String.format("%s: %s %d LP",user.getString("username"),rankedQueue.getString("tier"),rankedQueue.getInteger("leaguePoints")),"\u200b", false);
					    
				    }
			    }
			    


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

    private static EmbedCreateSpec.Builder getGames(Map<Document, Map<String, Number>> stats) {
        List<Document> rankedUsers;
        EmbedCreateSpec.Builder embed;
        rankedUsers = stats.keySet().stream()
                .sorted(Comparator.comparingDouble(u-> -stats.get(u)
                        .get("games").doubleValue())).collect(Collectors.toList());
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
                            stats.get(user).get("games").doubleValue()),
                    "\u200b", false);



        }
        return embed;
    }
    private static EmbedCreateSpec.Builder getLastPlayed(Map<Document, Map<String, Number>> stats, String mode, Double amount){
        List<Document> rankedUsers;
        EmbedCreateSpec.Builder embed;
        rankedUsers = stats.keySet().stream().sorted(Comparator.comparingLong(u->{
            return stats.get(u).get("lastPlayed").longValue();

        }).reversed()).collect(Collectors.toList());
        embed = EmbedCreateSpec.builder()
                .color(Color.GRAY)
                .title("Last Played")
                .description(String.format("Last %.0f, %s",amount,mode))
                .timestamp(Instant.now());
        for(int i =0;i<rankedUsers.size();i++){
            Document user = rankedUsers.get(i);
            embed.addField(String.format("%d. %s: %s",i+1,user.getString("username"),
                    new Date(stats.get(user).get("lastPlayed").longValue())),"\u200b",false);
        }
        return embed;


    }

    private static EmbedCreateSpec.Builder getLeaderboard(Map<Document, Map<String, Number>> stats,String mode, Double amount, String sortBy) {
        List<Document> rankedUsers;
        EmbedCreateSpec.Builder embed;
        rankedUsers = stats.keySet().stream().sorted(Comparator.comparingDouble(u->{
            switch (sortBy){
                case "placement":
                    return stats.get(u).get("placement").doubleValue()/ stats.get(u).get("games").doubleValue();

                case "wins":
                    return -stats.get(u).get("wins").doubleValue()/ stats.get(u).get("games").doubleValue();


            }
            return -1;


        })).collect(Collectors.toList());


        embed = EmbedCreateSpec.builder()
                .color(Color.GRAY)
                .title("Leaderboard")
                .description(String.format("Last %d\n%s, %s", amount.intValue(), sortBy,mode))

                //.addAllFields(users)
                .timestamp(Instant.now());

        for (int i =0;i<rankedUsers.size();i++) {
            Document user = rankedUsers.get(i);
            double val=0;
            if(sortBy.equals("placement")){
                val = stats.get(user).get("placement").doubleValue()/ stats.get(user).get("games").doubleValue();



            }
            else if(sortBy.equals("wins")){
                val = stats.get(user).get("wins").doubleValue()/stats.get(user).get("games").doubleValue();
            }
            embed.addField(String.format("%d. %s: %.2f",i+1,user.getString("username"),
                            val),
                    "\u200b", false);



        }
        return embed;
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
                                RequestHandler.getMatchData(matchId).map(matchData -> {

                                    for (Document server : serverCollection.find()) {
                                        System.out.println(server);
                                        for(Document participant:matchData.get("info",Document.class).getList("participants",Document.class)){
                                            client.getChannelById(Snowflake.of(server.getString("channel_id"))).ofType(MessageChannel.class)
                                                    .flatMap(messageChannel -> messageChannel
                                                            .createMessage(user.getString("username")+ "has concluded a match")).subscribe();
                                            if(participant.getString("puuid").equals(user.getString("puuid"))){
                                                if(participant.getInteger("placement")==8){
                                                    client.getChannelById(Snowflake.of(server.getString("channel_id"))).ofType(MessageChannel.class)
                                                            .flatMap(messageChannel -> messageChannel
                                                                    .createMessage(user.getString("username")+
                                                                            " recently got LAST!! HAHA what a LOSER! https://tenor.com/view/thanos-fortnite-takethel-dance-gif-12100688")).subscribe();

                                                }
                                            }
                                        }
                                    }
                                    return matchCollection.insertOne(matchData);

                                }).subscribe();



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
    public static Map<String,Number> getStats(double amount, Document user, String gameMode, MongoCollection<Document> matchCollection) {
        Map<String,Number> stats = new HashMap<>();
//        List<String> matches = user.getList("match_history",String.class).subList(0,(int)amount);
        List<String> matches = user.getList("match_history",String.class);
        double placementTotal=0;
        double firsts = 0;
        double games = 0;
        long lastPlayed=0;

        for(String matchId: matches){
            Document data = matchCollection.find(eq("match_id",matchId)).first();
            if(amount==0){
                break;
            }

            Document info = data.get("info",Document.class);
            int queue_id = info.getInteger("queue_id");
            if(!gameMode.equals("all")){
                if(gameMode.equals("doubleup")&&queue_id!=1150){
                    continue;
                }else if(gameMode.equals("ranked")&&queue_id!=1100){
                    continue;
                }else if(gameMode.equals("normal")&&queue_id!=1090){
                    continue;
                }


            }



            for(Document participant:info.getList("participants",Document.class)){

                if(participant.getString("puuid").equals(user.getString("puuid"))){
                    lastPlayed=info.getLong("game_datetime");


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
        stats.put("lastPlayed",lastPlayed);
        return stats;

    }
}
