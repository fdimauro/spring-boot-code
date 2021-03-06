package io.pivotal.workshop.snippet.config;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.pivotal.workshop.snippet.domain.Person;

@EnableConfigurationProperties(SnippetProperties.class)
@Configuration
public class SnippetSecurityConfig extends WebSecurityConfigurerAdapter {

    private final Logger log = LoggerFactory.getLogger(SnippetSecurityConfig.class);
    private RestTemplate restTemplate;
    private UriComponentsBuilder builder;
    private SnippetProperties properties;

    public SnippetSecurityConfig(RestTemplateBuilder restTemplateBuilder,SnippetProperties properties){
        this.restTemplate = restTemplateBuilder.basicAuthorization(properties.getAuthenticationUsername(),properties.getAuthenticationPassword()).build();
        this.properties = properties;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .requestMatchers(EndpointRequest.to("status", "info"))
                .permitAll()

                .requestMatchers(EndpointRequest.toAnyEndpoint())
                .hasRole("ACTUATOR")


                .requestMatchers(PathRequest.toStaticResources().atCommonLocations())
                .permitAll()

                .antMatchers("/api/**").hasRole("ADMIN")
                .antMatchers("/").hasRole("USER")

                .and()
                .httpBasic();
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception { ////<1>
        auth.userDetailsService(new UserDetailsService(){

            @Override
            public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

                try {
                    builder = UriComponentsBuilder.fromUriString(properties.getAuthenticationUri())
                            .queryParam("email", email);

                    log.info("Querying: " + builder.toUriString());

                    ResponseEntity<Resource<Person>> responseEntity = restTemplate.exchange(  ////<2>
                            RequestEntity.get(URI.create(builder.toUriString()))
                                .accept(MediaTypes.HAL_JSON)
                                .build()
                            , new ParameterizedTypeReference<Resource<Person>>() {
                            });

                    if (responseEntity.getStatusCode() == HttpStatus.OK) {

                        Resource<Person> resource = responseEntity.getBody();
                        Person person = resource.getContent();

                        //This should be used outside.
                        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
                        String password = encoder.encode(person.getPassword());

                        return User.withUsername(person.getEmail()).password(password).roles(person.getRole()).build();
                    }

                }catch(Exception ex) {
                    ex.printStackTrace();
                }

                throw new UsernameNotFoundException(email);


            }
        });
    }

}
