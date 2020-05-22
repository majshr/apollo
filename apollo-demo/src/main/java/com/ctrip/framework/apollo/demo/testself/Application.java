package com.ctrip.framework.apollo.demo.testself;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.demo.testself.config.RequestConfig;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;

@SpringBootApplication
@EnableApolloConfig(value = { "application", "namespace-private", "TEST1.namespace-1" })
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Component
    public class OrderPropertiesCommandLineRunner implements CommandLineRunner {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Autowired
        private RequestConfig requestConfig;

        @Override
        public void run(String... args) {
            logger.info("payTimeoutSeconds:" + requestConfig.getTimeout());
        }

    }

    @Component
    public class ValueCommandLineRunner implements CommandLineRunner {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Value(value = "${request.timeout}")
        private Integer requestTimeout;


        @Override
        public void run(String... args) {
            logger.info("payTimeoutSeconds:" + requestTimeout);
        }
    }
}
