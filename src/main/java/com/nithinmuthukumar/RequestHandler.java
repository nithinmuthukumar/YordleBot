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

    static HttpClient client=HttpClient.create();



    static String riotKey = System.getenv("riotKey") ;
    static Gson gson = new Gson();
    public static Mono<Document> getRank(String summonerId){
        return sendRequest("https://na1.api.riotgames.com/tft/league/v1/entries/by-summoner/"+summonerId)
                .map(json -> {
                    return Document.parse("{queues:"+json+"}");
                });
    }



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
			System.out.println(url);
                        System.out.println(status.responseHeaders());
                        return null;

                    }
                    return res.asString();
                });
    }
}
