package org.example;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.MemPoolProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput})
@OutputTimeUnit(TimeUnit.SECONDS)
public class MailBench {

    private static final int COUNT = 10;

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        volatile Mailer SENDER_SIMPLE;
        volatile JavaMailSenderImpl SENDER_SPRING;
        volatile Email MSG_SIMPLE;
        volatile MimeMessage MSG_SPRING;

        @Setup(Level.Trial)
        public void doSetup() {
            // configure sender simple
            SENDER_SIMPLE = MailerBuilder
                    .withSMTPServerHost("localhost")
                    .withSMTPServerPort(8025)
                    .withSMTPServerUsername("myuser")
                    .withSMTPServerPassword("mysecretpassword")
                    .withTransportStrategy(TransportStrategy.SMTP)
                    .withConnectionPoolCoreSize(10)
                    .withThreadPoolSize(10)
                    .withConnectionPoolMaxSize(10)
//                    .withDebugLogging(true)
                    .async()
                    .buildMailer();

            // configure sender spring
            var mailSender = new JavaMailSenderImpl();
            mailSender.setHost("localhost");
            mailSender.setPort(8025);
            mailSender.setProtocol("smtp");
            mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
            mailSender.setUsername("myuser");
            mailSender.setPassword("mysecretpassword");
            SENDER_SPRING = mailSender;

            // create emails
            MSG_SIMPLE = EmailBuilder.startingBlank()
                    .from("qwe@example.com")
                    .withSubject("Test java sender client: simple")
                    .appendText("test body")
                    .to("recipient@example.com")
                    .buildEmail();

            try {
                var emailSpring = mailSender.createMimeMessage();
                var messageHelper = new MimeMessageHelper(emailSpring);
                messageHelper.setSubject("Test java sender client: spring");
                messageHelper.setText("test body");
                messageHelper.addTo("recipient@example.com");
                messageHelper.setFrom("qwe@example.com");
                MSG_SPRING = messageHelper.getMimeMessage();
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }

        @TearDown(Level.Trial)
        public void close() {
            SENDER_SIMPLE.shutdownConnectionPool();
        }
    }

    @Benchmark
    public void simple(BenchmarkState state) {
        var features = new ArrayList<CompletableFuture<Void>>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            features.add(state.SENDER_SIMPLE.sendMail(state.MSG_SIMPLE));
        }

        CompletableFuture.allOf(features.toArray(new CompletableFuture[0])).join();
    }

//    @Benchmark
//    public void spring(BenchmarkState state) {
//        for (int i = 0; i < COUNT; i++) {
//            // configure sender spring
//            var mailSender = new JavaMailSenderImpl();
//            mailSender.setHost("localhost");
//            mailSender.setPort(8025);
//            mailSender.setProtocol("smtp");
//            mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
//            mailSender.setUsername("myuser");
//            mailSender.setPassword("mysecretpassword");
//
//            try {
//                var emailSpring = mailSender.createMimeMessage();
//                var messageHelper = new MimeMessageHelper(emailSpring);
//                messageHelper.setSubject("Test java sender client: spring");
//                messageHelper.setText("test body");
//                messageHelper.addTo("recipient@example.com");
//                messageHelper.setFrom("qwe@example.com");
//                mailSender.send(messageHelper.getMimeMessage());
//            } catch (MessagingException e) {
//                throw new RuntimeException(e);
//            }
////            state.SENDER_SPRING.send(state.MSG_SPRING);
//        }
//    }

    @Benchmark
    public void spring(BenchmarkState state) {
        for (int i = 0; i < COUNT; i++) {
            state.SENDER_SPRING.send(state.MSG_SPRING);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MailBench.class.getSimpleName())
                .addProfiler(MemPoolProfiler.class)
                .addProfiler(GCProfiler.class)
                .forks(1)
                .warmupIterations(0)
                .measurementIterations(1)
                .resultFormat(ResultFormatType.JSON)
                .build();

        new Runner(opt).run();
    }
}