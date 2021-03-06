package io.ph.bot.commands.japanese;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.codec.binary.Base64;
import org.json.XML;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import io.ph.bot.Bot;
import io.ph.bot.commands.Command;
import io.ph.bot.commands.CommandData;
import io.ph.bot.exception.NoAPIKeyException;
import io.ph.bot.model.Guild;
import io.ph.bot.model.Permission;
import io.ph.bot.model.anime.MALAnime;
import io.ph.util.MessageUtils;
import io.ph.util.Util;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

/**
 * Search for an anime from MAL by name
 * @author Paul
 * TODO: Clean this up. Was quickly ported over from an old bot, so it looks pretty bad
 */
@CommandData (
		defaultSyntax = "mal",
		aliases = {},
		permission = Permission.NONE,
		description = "Search for an anime from MyAnimeList.net\n",
		example = "shinsekai yori"
		)
public class AnimeSearch implements Runnable, Command {

	private IMessage originalMessage;

	private static String baseMalUrl = "https://myanimelist.net/api/";

	public AnimeSearch() { }

	public AnimeSearch(IMessage msg) {
		this.originalMessage = msg;
	}

	@Override
	public void executeCommand(IMessage msg) {
		Runnable a = new AnimeSearch(msg);
		new Thread(a).start();
	}

	public void process(IMessage msg) {
		JsonValue jv = null;
		JsonArray ja = null;
		EmbedBuilder em = new EmbedBuilder();
		String search = Util.getCommandContents(msg);
		if(search.equals("")) {
			MessageUtils.sendMessage(msg.getChannel(),
					MessageUtils.commandErrorMessage(msg, "anime", "anime-name", "**anime-name** - name of the anime you are searching for").build());
			return;
		}
		if(Util.isInteger(search)) {
			Guild g = Guild.guildMap.get(msg.getGuild().getID());
			int given = Integer.parseInt(search);
			if((given) > g.getHistoricalSearches().getHistoricalAnime().size() || given < 1) {
				MessageUtils.sendErrorEmbed(msg.getChannel(), "Invalid input",
						"Giving a number will provide detailed information on a previous "
								+ Util.getPrefixForGuildId(msg.getGuild().getID()) + "anime search. This # is too large");
				return;
			}
			try {
				jv = getMalJson(g.getHistoricalSearches().getHistoricalAnime().get(given));
				JsonObject temp = jv.asObject().get("anime").asObject();
				ja = temp.get("entry").isArray() 
						? new JsonArray().add(new JsonObject().add("entry", temp.get("entry").asArray().get(0).asObject()))
								: new JsonArray().add(temp);

			} catch (IOException e) {
				e.printStackTrace();
			} catch (NoAPIKeyException e) {
				MessageUtils.sendErrorEmbed(msg.getChannel(), "Error", "Sorry, looks like this bot isn't setup to do Anime lookups");
				return;
			}

		} else {
			try {
				jv = getMalJson(search);
				JsonObject temp = jv.asObject().get("anime").asObject();
				ja = temp.get("entry").isArray() ? temp.get("entry").asArray() : new JsonArray().add(temp);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NoAPIKeyException e) {
				MessageUtils.sendErrorEmbed(msg.getChannel(), "Error", "Sorry, looks like this bot isn't setup to do Anime lookups");
				return;
			} catch (NullPointerException e) {
				MessageUtils.sendErrorEmbed(msg.getChannel(), "Sorry!",
						"Couldn't find any anime for **" + search + "**");
				return;
			}
			if(ja == null || ja.size() == 0) {
				MessageUtils.sendErrorEmbed(msg.getChannel(), "Sorry!",
						"Couldn't find any anime for **" + search + "**");
				return;
			}
		}
		//Single anime result - give detailed info
		if(ja.size() == 1) {
			try {
				MALAnime a = new MALAnime(ja.get(0).asObject().get("entry").asObject());
				StringBuilder sb = new StringBuilder();
				em.withAuthorName(a.getTitle());
				em.withAuthorUrl(a.getMalLink());
				if(a.getType() != null)
					sb.append("**Type**: " + a.getType() + "\n");
				if(a.getAiringStatus().startsWith("Curr")) {
					if(a.getEndDate() == null)
						sb.append("Began on **" + a.getStartDate() + "** and is currently airing\n");
					else
						sb.append("Began on **" + a.getStartDate() + "** and will finish on " + a.getEndDate() + "\n");
				} else if(a.getAiringStatus().startsWith("Fini")) {
					sb.append("Began on **" + a.getStartDate() + "** and ended on **" + a.getEndDate() + "**\n");
				} else {
					if(a.getEndDate() == null && a.getStartDate() == null) 
						sb.append("Not yet aired\n");
					else if(a.getStartDate() != null && a.getEndDate() == null)
						sb.append("Not yet aired. Will start on " + a.getStartDate() + "\n");
					else
						sb.append("Not yet aired. Will start on " + a.getStartDate() + " and end on " + a.getEndDate() + "\n");
				}
				sb.append("**MAL Rating**: " + a.getMalRating() + "/10\n");
				sb.append("**Synopsis**: " + a.getSynopsis()+"\n");

				sb.append(a.getMalLink());
				em.withDesc(sb.toString());
				em.withImage(a.getImageLink());
				em.withColor(Color.GREEN);
				MessageUtils.sendMessage(msg.getChannel(), em.build());
			} catch (NoAPIKeyException e) {
				MessageUtils.sendErrorEmbed(msg.getChannel(), "Error", "Sorry, looks like this bot isn't setup to do Anime lookups");
			}
		} else {
			int count = 1;
			StringBuilder sb = new StringBuilder();
			em.withTitle("Multiple results found");
			Guild.guildMap.get(msg.getGuild().getID()).getHistoricalSearches().clearAnimeSearches();
			for(JsonValue jv2 : ja) {
				if(count > 15) {
					sb.append("*limited to 15 results*\n");
					break;
				}
				JsonObject jo = jv2.asObject();
				Guild.guildMap.get(msg.getGuild().getID()).getHistoricalSearches()
					.addHistoricalAnime(count, new Object[]{jo.getString("title", "cowboy bebop"), jo.getInt("id", -1)});
				sb.append("**" + count + ")** " + jo.get("title").asString() 
						+ " | http://myanimelist.net/anime/" + jo.get("id").asInt() + "\n");
				count++;
			}
			em.withFooterText("use " + Util.getPrefixForGuildId(msg.getGuild().getID()) + "anime # to search from this list");
			em.withColor(Color.WHITE);
			em.withDesc(sb.toString());
			MessageUtils.sendMessage(msg.getChannel(), em.build());
		}

	}

	private JsonValue getMalJson(String search) throws NoAPIKeyException, IOException {
		try {
			String query = baseMalUrl + "anime/search.xml?q=" + URLEncoder.encode(search, "UTF-8");
			HttpsURLConnection conn = (HttpsURLConnection) new URL(query).openConnection();
			String userpass;

			userpass = Bot.getInstance().getApiKeys().get("maluser") + ":" 
					+ Bot.getInstance().getApiKeys().get("malpass");

			String basicAuth = "Basic " + new String(Base64.encodeBase64String(userpass.getBytes()));
			conn.setRequestProperty("Authorization", basicAuth);
			conn.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");
			conn.connect();
			StringBuilder stb = new StringBuilder();
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				stb.append(line);
			}
			return Json.parse(XML.toJSONObject(stb.toString()).toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private JsonValue getMalJson(Object[] o) throws NoAPIKeyException, IOException {
		try {
			String query = baseMalUrl + "anime/search.xml?q=" + URLEncoder.encode((String) o[0], "UTF-8");
			HttpsURLConnection conn = (HttpsURLConnection) new URL(query).openConnection();
			String userpass;

			userpass = Bot.getInstance().getApiKeys().get("maluser") + ":" 
					+ Bot.getInstance().getApiKeys().get("malpass");

			String basicAuth = "Basic " + new String(Base64.encodeBase64String(userpass.getBytes()));
			conn.setRequestProperty("Authorization", basicAuth);
			conn.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");
			conn.connect();
			StringBuilder stb = new StringBuilder();
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				stb.append(line);
			}
			JsonValue toReturn = Json.parse(XML.toJSONObject(stb.toString()).toString());
			if(((int) o[1]) != -1 && toReturn.asObject().get("anime").asObject().get("entry").isArray()) {
				List<Integer> toRemove = new ArrayList<Integer>();
				int count = 0;
				for(JsonValue jv : toReturn.asObject().get("anime").asObject().get("entry").asArray()) {
					if(jv.asObject().getInt("id", -1) != (int) o[1]) {
						toRemove.add(count);
					}
					count++;
				}
				for(int i = toRemove.size() - 1; i >= 0; i--) {
					toReturn.asObject().get("anime").asObject().get("entry").asArray().remove(toRemove.get(i));
				}
			}
			
			return toReturn;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void run() {
		process(this.originalMessage);
	}


}
