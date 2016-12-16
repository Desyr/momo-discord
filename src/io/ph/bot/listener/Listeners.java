package io.ph.bot.listener;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import io.ph.bot.Bot;
import io.ph.bot.State;
import io.ph.bot.commands.Command;
import io.ph.bot.commands.CommandHandler;
import io.ph.bot.events.UserBanEvent;
import io.ph.bot.events.UserMutedEvent;
import io.ph.bot.events.UserUnmutedEvent;
import io.ph.bot.model.Guild;
import io.ph.bot.scheduler.JobScheduler;
import io.ph.util.MessageUtils;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ChannelCreateEvent;
import sx.blah.discord.handle.impl.events.GuildCreateEvent;
import sx.blah.discord.handle.impl.events.GuildLeaveEvent;
import sx.blah.discord.handle.impl.events.MentionEvent;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.NickNameChangeEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.UserJoinEvent;
import sx.blah.discord.handle.impl.events.UserLeaveEvent;
import sx.blah.discord.handle.impl.events.UserPardonEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

public class Listeners {
	@EventSubscriber
	public void onReadyEvent(ReadyEvent e) {
		if(!Bot.getInstance().getBot().getOurUser().getName().equals(Bot.getInstance().getUsername()))
			State.changeBotUsername(Bot.getInstance().getUsername());
		State.changeBotAvatar(new File("resources/avatar/" + Bot.getInstance().getAvatar()));
		
		int connectedVoice = 0;
		for(IGuild guild : Bot.getInstance().getBot().getGuilds()) {
			Guild g = Guild.guildMap.get(guild.getID());
			if(g.getSpecialChannels().getVoice().length() > 0) {
				IVoiceChannel target = guild.getVoiceChannelByID(g.getSpecialChannels().getVoice());
				if(target == null)
					return;
				try {
					target.join();
					g.initMusicManager(guild);
					connectedVoice++;
				} catch (MissingPermissionsException e1) {
					e1.printStackTrace();
				}
			}
		}
		LoggerFactory.getLogger(Listeners.class).info("Connected to {} music channels", connectedVoice);
		JobScheduler.initializeEventSchedule();
		Bot.getInstance().getLogger().info("Bot is now online");
	}

	@EventSubscriber
	public void onMessageRecievedEvent(MessageReceivedEvent e) {
		if(e.getMessage().getGuild() == null) {
			// Private message
			EmbedBuilder em = new EmbedBuilder();
			Command c;
			if((c = CommandHandler.getCommand(e.getMessage().getContent())) == null) {
				em.withTitle("Invalid command").withColor(Color.RED).withDesc(e.getMessage().getContent() + " is not a valid command");
				MessageUtils.sendPrivateMessage(e.getMessage().getAuthor(), em.build());
				return;
			}
			em.withTitle(e.getMessage().getContent()).withColor(Color.CYAN).appendField("Primary Command", c.getDefaultCommand(), true);
			String[] aliases = c.getAliases();
			if(aliases.length > 0) {
				em.appendField("Aliases", 
						Arrays.toString(aliases).substring(1, Arrays.toString(aliases).length() - 1) + "\n", true);
			}
			em.appendField("Permissions", c.getPermission().toString(), true).appendField("Description", c.getDescription(), false).
				appendField("Example", c.getDefaultCommand() + " " + c.getExample(), false);
			MessageUtils.sendPrivateMessage(e.getMessage().getAuthor(), em.build());
			return;
		}
		if(e.getMessage().getContent().startsWith(Guild.guildMap.get(e.getMessage().getGuild().getID())
				.getGuildConfig().getCommandPrefix())) {
			CommandHandler.processCommand(e.getMessage());
		}
		try {
			if(e.getMessage().getContent().equalsIgnoreCase("shit")) {
				e.getMessage().addReaction("💩");
			}
		} catch (MissingPermissionsException | RateLimitException | DiscordException e1) {
			e1.printStackTrace();
		}

	}

	/**
	 * Guild create event fires when a guild connects or the bot joins the guild
	 * @param e
	 */
	@EventSubscriber
	public void onGuildCreateEvent(GuildCreateEvent e) {
		File f;
		if(!(f = new File("resources/guilds/" + e.getGuild().getID() +"/")).exists()) {
			try {
				FileUtils.forceMkdir(f);
				FileUtils.copyFile(new File("resources/guilds/template.properties"), 
						new File("resources/guilds/" + e.getGuild().getID()+"/GuildProperties.properties"));
				FileUtils.copyFile(new File("resources/guilds/template.db"), 
						new File("resources/guilds/" + e.getGuild().getID()+"/Data.db"));
				FileUtils.copyFile(new File("resources/guilds/template.json"), 
						new File("resources/guilds/" + e.getGuild().getID()+"/IdlePlaylist.json"));

				Bot.getInstance().getLogger().info("Guild has joined: {}", e.getGuild().getName());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		Guild g = new Guild(e.getGuild());
		if(g.getGuildConfig().isFirstTime()) {
			//TODO: Better intro
			MessageUtils.sendMessage(e.getGuild().getChannels().get(0), "'allo, I'm a bot!\n"
					+ "Command prefix: $ | Try $info");
			Guild.guildMap.get(e.getGuild().getID()).getGuildConfig().setFirstTime(false);
		}
	}

	@EventSubscriber
	public void onGuildLeaveEvent(GuildLeaveEvent e) {
		try {
			FileUtils.deleteDirectory(new File("resources/guilds/" + e.getGuild().getID() + "/"));
			Bot.getInstance().getLogger().info("Guild has left: {}", e.getGuild().getName());
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	@EventSubscriber
	public void onUserJoinListener(UserJoinEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		if(!g.getSpecialChannels().getLog().equals("")) {
			EmbedBuilder em = new EmbedBuilder().withAuthorIcon(e.getUser().getAvatarURL())
					.withAuthorName(e.getUser().getName() + " has joined the server")
					.withColor(Color.GREEN).withTimestamp(System.currentTimeMillis());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
		}
		if(!g.getSpecialChannels().getWelcome().equals("")) {
			String msg = g.getGuildConfig().getWelcomeMessage();
			msg = msg.replaceAll("\\$user\\$", "<@"+e.getUser().getID()+">");
			msg = msg.replaceAll("\\$server\\$", e.getGuild().getName());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getWelcome()), msg, true);
		}
	}

	@EventSubscriber
	public void onUserLeaveListener(UserLeaveEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		if(!g.getSpecialChannels().getLog().equals("")) {
			EmbedBuilder em = new EmbedBuilder().withAuthorIcon(e.getUser().getAvatarURL())
					.withAuthorName(e.getUser().getName() + " has left the server")
					.withColor(Color.RED).withTimestamp(System.currentTimeMillis());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
		}
	}

	@EventSubscriber
	public void onNicknameChangeListener(NickNameChangeEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		EmbedBuilder em = new EmbedBuilder().withAuthorIcon(e.getUser().getAvatarURL())
				.withColor(Color.CYAN).withTimestamp(System.currentTimeMillis());
		if(!g.getSpecialChannels().getLog().equals("")) {
			if(e.getOldNickname().isPresent() && e.getNewNickname().isPresent()) {
				em.withDesc("**" + e.getOldNickname().get() + "** to **" + e.getNewNickname().get() + "**");
				em.withAuthorName(e.getUser().getName() + " changed their nickname");
			} else if(e.getOldNickname().isPresent() && !e.getNewNickname().isPresent()) {
				em.withDesc("**" + e.getOldNickname().get() + "** to **" + e.getUser().getName() + "**");
				em.withAuthorName(e.getUser().getName() + " removed their nickname");
			} else if(!e.getOldNickname().isPresent() && e.getNewNickname().isPresent()) {
				em.withDesc("**" + e.getUser().getName() + "** to **" + e.getNewNickname().get() + "**");
				em.withAuthorName(e.getUser().getName() + " added a nickname");
			}
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
		}
	}

	@EventSubscriber
	public void onUserBanEvent(UserBanEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		if(!g.getSpecialChannels().getLog().equals("")) {
			EmbedBuilder em = new EmbedBuilder().withAuthorIcon(e.getUser().getAvatarURL())
					.withAuthorName(e.getUser().getName() + " has been banned by " + e.getBanner().getName())
					.withColor(Color.RED).withTimestamp(System.currentTimeMillis());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
		}
	}

	@EventSubscriber
	public void onUserPardonEvent(UserPardonEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		if(!g.getSpecialChannels().getLog().equals("")) {
			try {
			EmbedBuilder em = new EmbedBuilder().withTitle(e.getUser().getName() + " has been unbanned")
					.withColor(Color.GREEN).withTimestamp(System.currentTimeMillis());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
			} catch(NullPointerException e1) {
				// Throws a NPE when the bot pardons, but still lets the message go through. Weird, D4j bug?
			}
		}
	}


	@EventSubscriber
	public void onChannelCreateEvent(ChannelCreateEvent e) {
		Guild g = Guild.guildMap.get(e.getChannel().getGuild().getID());
		if(!g.getMutedRoleId().equals("")) {
			IRole target;
			if((target = e.getChannel().getGuild().getRoleByID(g.getMutedRoleId())) == null)
				return;
			try {
				e.getChannel().overrideRolePermissions(target,
						Permissions.getDeniedPermissionsForNumber(0), Permissions.getAllowedPermissionsForNumber(2048));
			} catch (Exception e1) {
				e1.printStackTrace();
			} 
		}
	}

	@EventSubscriber
	public void onMentionEvent(MentionEvent e) {
		Guild g = Guild.guildMap.get(e.getMessage().getGuild().getID());
		if(g.getCleverBot() != null) {
			String msg = e.getMessage().getContent().replaceAll("<@" + Bot.getInstance().getBot().getOurUser().getID() + ">", "");
			if(msg.trim().equals("reset")) {
				g.resetCleverBot();
				return;
			}
			try {
				String s = g.getCleverBot().think(msg);
				MessageUtils.sendMessage(e.getMessage().getChannel(), s);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	@EventSubscriber
	public void onUserMutedEvent(UserMutedEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		if(!g.getSpecialChannels().getLog().equals("")) {
			EmbedBuilder em = new EmbedBuilder().withAuthorIcon(e.getUser().getAvatarURL())
					.withAuthorName(e.getUser().getName() + " has been muted by " + e.getMuter().getName())
					.withColor(Color.RED).withTimestamp(System.currentTimeMillis());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
		}
	}
	
	@EventSubscriber
	public void onUserUnmutedEvent(UserUnmutedEvent e) {
		Guild g = Guild.guildMap.get(e.getGuild().getID());
		if(!g.getSpecialChannels().getLog().equals("")) {
			EmbedBuilder em = new EmbedBuilder().withAuthorIcon(e.getUser().getAvatarURL())
					.withAuthorName(e.getUser().getName() + " has been unmuted")
					.withColor(Color.GREEN).withTimestamp(System.currentTimeMillis());
			MessageUtils.sendMessage(e.getGuild().getChannelByID(g.getSpecialChannels().getLog()), em.build());
		}
	}
}