package com.helpdesk.email;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sends a real email over SMTP to Mailpit, then reads it back through Mailpit's API. */
@Testcontainers
class SmtpEmailSenderTest {

	@Container
	static final GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.3"))
		.withExposedPorts(1025, 8025)
		.waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

	private static final HttpClient http = HttpClient.newHttpClient();

	private static final JsonMapper json = JsonMapper.builder().build();

	@Test
	void sendsPlainTextEmailWithTicketSubject() throws Exception {
		sender(mailpit.getMappedPort(1025)).send(new OutboundEmail("sam@example.com", "Re: [#7] Video will not load",
				"Hi Sam,\nclear your browser cache and reload the lesson.", 7L));

		JsonNode message = get("/api/v1/messages").get("messages").get(0);
		assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo("sam@example.com");
		assertThat(message.get("From").get("Address").asString()).isEqualTo("support@helpdesk.local");
		assertThat(message.get("From").get("Name").asString()).isEqualTo("HelpDesk Support");
		assertThat(message.get("Subject").asString()).isEqualTo("Re: [#7] Video will not load");
		JsonNode full = get("/api/v1/message/" + message.get("ID").asString());
		assertThat(full.get("Text").asString()).contains("clear your browser cache and reload the lesson.");
	}

	@Test
	void failsLoudlyWhenTheServerIsUnreachable() {
		// Nothing listens on port 1 locally: the send job must see a failure so it retries.
		assertThatThrownBy(() -> sender(1).send(new OutboundEmail("sam@example.com", "Re: [#7] Hi", "Hello", 7L)))
			.isInstanceOf(MailSendException.class);
	}

	private static SmtpEmailSender sender(int port) {
		JavaMailSenderImpl mail = new JavaMailSenderImpl();
		mail.setHost(mailpit.getHost());
		mail.setPort(port);
		mail.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
		HelpdeskProperties properties = new HelpdeskProperties(null, null, null, null,
				new HelpdeskProperties.Email("smtp", "HelpDesk Support <support@helpdesk.local>", 1, Duration.ZERO));
		return new SmtpEmailSender(mail, properties);
	}

	private static JsonNode get(String path) throws Exception {
		URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).build(),
				HttpResponse.BodyHandlers.ofString());
		return json.readTree(response.body());
	}

}
