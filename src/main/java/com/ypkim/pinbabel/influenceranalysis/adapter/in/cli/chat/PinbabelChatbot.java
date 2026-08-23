package com.ypkim.pinbabel.influenceranalysis.adapter.in.cli.chat;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.identity.User;
import com.embabel.agent.core.Budget;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.ChatSession;
import com.embabel.chat.ChatTrigger;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.support.InMemoryConversationFactory;
import com.ypkim.pinbabel.influenceranalysis.adapter.in.cli.PinbabelCliRenderer;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.AnalyzeInfluencerPostsUseCase;
import com.ypkim.pinbabel.influenceranalysis.application.port.in.dto.AnalyzeInfluencerPostsCommand;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("fixture & cli")
@PrimaryAdapter
public class PinbabelChatbot implements Chatbot {

	private final AnalyzeInfluencerPostsUseCase useCase;
	private final PinbabelCliRenderer renderer;
	private final InMemoryConversationFactory conversationFactory = new InMemoryConversationFactory();
	private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

	public PinbabelChatbot(AnalyzeInfluencerPostsUseCase useCase, PinbabelCliRenderer renderer) {
		this.useCase = useCase;
		this.renderer = renderer;
	}

	@Override
	public ChatSession createSession(
		User user,
		OutputChannel outputChannel,
		String contextId,
		String conversationId,
		Budget budget
	) {
		var id = conversationId == null || conversationId.isBlank()
			? UUID.randomUUID().toString()
			: conversationId;
		var session = new DomainChatSession(user, outputChannel, conversationFactory.create(id));
		sessions.put(id, session);
		return session;
	}

	@Override
	public ChatSession findSession(String conversationId) {
		return sessions.get(conversationId);
	}

	private final class DomainChatSession implements ChatSession {

		private final User user;
		private final OutputChannel outputChannel;
		private final Conversation conversation;

		private DomainChatSession(User user, OutputChannel outputChannel, Conversation conversation) {
			this.user = user;
			this.outputChannel = outputChannel;
			this.conversation = conversation;
		}

		@Override
		public OutputChannel getOutputChannel() {
			return outputChannel;
		}

		@Override
		public User getUser() {
			return user;
		}

		@Override
		public Conversation getConversation() {
			return conversation;
		}

		@Override
		public void onUserMessage(UserMessage userMessage) {
			conversation.addMessage(userMessage);
			var resource = useCase.analyze(new AnalyzeInfluencerPostsCommand(userMessage.getContent()));
			send(renderer.render(resource));
		}

		@Override
		public void onTrigger(ChatTrigger trigger) {
			var resource = useCase.analyze(new AnalyzeInfluencerPostsCommand(trigger.getPrompt()));
			send(renderer.render(resource));
		}

		private void send(String content) {
			var message = new AssistantMessage(content);
			conversation.addMessage(message);
			outputChannel.send(new MessageOutputChannelEvent(conversation.getId(), message));
		}
	}
}
