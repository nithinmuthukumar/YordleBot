package com.nithinmuthukumar;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.bson.Document;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;


import javax.print.Doc;
import java.time.Duration;
import java.util.ArrayList;

public class RequestHandler {
    static int timer = 0;
    static HttpClient client=HttpClient.create();



    static String riotKey = System.getenv("riotKey") ;
    static Gson gson = new Gson();



    public static Mono<Document> getSummoner(String username){
        return sendRequest("https://na1.api.riotgames.com/tft/summoner/v1/summoners/by-name/"+username)
                .map(response->{
                    Document summoner = Document.parse(response);


                    return summoner;
                });





    }
    public static Mono<ArrayList<String>> getMatches(int amount, String puuid){
        return sendRequest("https://americas.api.riotgames.com/tft/match/v1/matches/by-puuid/"+String.format("%s/ids?count=%d",puuid,amount))
                .map(response->{
                    JsonArray matchArray = gson.fromJson(response, JsonArray.class);
                    ArrayList<String> matches = new ArrayList();


                    matchArray.forEach(s->matches.add(s.getAsString()));

                    return matches;

                });





    }
    public static Mono<Document> getMatchData(String matchId){
        return sendRequest("https://americas.api.riotgames.com/tft/match/v1/matches/"+matchId)
                .map(response->{

                    Document document = Document.parse(response);
                    if(response!=null) document.append("match_id",matchId);
                    return document;
                });

    }


    public static Mono<String> sendRequest(String url){
        return client.headers(h ->h.add("Content-Type","application/json").add("X-Riot-Token",riotKey)).get().uri(url)
                .responseSingle((status,res)-> {
                    if(!status.status().equals(HttpResponseStatus.OK)){
                        System.out.println(status.status());
                        System.out.println("RateLimited");
                        System.out.println(status.responseHeaders());
                        return null;

                    }
//                    //Handling rate limits
//                    String[] appLimits = status.responseHeaders().get("X-App-Rate-Limit-Count").split(",");
//                    // requests per second
//                    double sValue = Integer.valueOf(appLimits[0].split(":")[0]);
//
//                    // requests per 2 minutes
//                    double mValue = Integer.valueOf(appLimits[1].split(":")[0]);
//                    System.out.println("sValue "+sValue);
//                    System.out.println("mValue "+mValue);
//
//                    if(sValue>=18){
//                        timer = 2;
//                        System.out.println("Delay");
//                    }else if(mValue>95){
//                        timer = 125;
//                        System.out.println("Delay");
//                    }else{
//                        timer = 0;
//                    }




                    return res.asString();
                }).delayElement(Duration.ofMillis(2000));
    }
}
