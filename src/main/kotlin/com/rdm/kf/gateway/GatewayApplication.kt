package com.rdm.kf.gateway

import com.rdm.kf.routes.RoutesAPI
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec
import org.springframework.cloud.gateway.route.builder.PredicateSpec
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.server.SecurityWebFilterChain
import java.net.URI

@SpringBootApplication
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}

@Bean
fun redisRateLimiter(): RedisRateLimiter {
    return RedisRateLimiter(5, 10)
}

@Bean
fun authorization(https: ServerHttpSecurity): SecurityWebFilterChain {
    return https
            .httpBasic(Customizer.withDefaults())
            .csrf(CsrfSpec::disable)
            .authorizeExchange(fun(ae: AuthorizeExchangeSpec) {
                ae.pathMatchers("/${RoutesAPI.API_PATH_SCHOOL}").authenticated()
                        .anyExchange().permitAll()
            })
            .build()
}

@Bean
fun authentication(): MapReactiveUserDetailsService {
    return MapReactiveUserDetailsService(User.builder()
            .username("rdm")
            .password("rdm")
            .roles("USER")
            .build())
}

@Bean
fun gateway(rlb: RouteLocatorBuilder): RouteLocator {
    return rlb.routes()
            .route { rs: PredicateSpec ->
                rs.path("/default").filters { fs: GatewayFilterSpec ->
                    fs.filter { exchange, chain ->
                        chain.filter(exchange)
                    }
                }.uri("lb://${RoutesAPI.API_SCHOOL}")
            }
            .route { rs: PredicateSpec ->
                rs.path("/${RoutesAPI.API_PATH_SCHOOL}")
                        .filters { fs: GatewayFilterSpec ->
                            fs.circuitBreaker {
                                it?.fallbackUri = URI("forward:/default")
                            }
                                    .requestRateLimiter {
                                        it.rateLimiter = redisRateLimiter()
                                        it.keyResolver = PrincipalNameKeyResolver()
                                    }
                        }.uri("lb://${RoutesAPI.API_SCHOOL}")
            }
            .route { rs: PredicateSpec ->
                rs.path("/${RoutesAPI.API_PATH_SCHOOL}")
                        .uri("lb://${RoutesAPI.API_SCHOOL}")
            }
            .build()
}
