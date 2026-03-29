package com.example.cache.balancer.api;

import com.example.cache.balancer.client.MasterResponse;
import com.example.cache.balancer.routing.CacheRouter;
import com.example.cache.balancer.routing.RoutingInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cache")
public class CacheRouterController {

    private final CacheRouter cacheRouter;

    public CacheRouterController(CacheRouter cacheRouter) {
        this.cacheRouter = cacheRouter;
    }

    @GetMapping("/{key}")
    public ResponseEntity<String> get(@PathVariable String key) {
        MasterResponse response = cacheRouter.routeGet(key);
        return toResponseEntity(response);
    }

    @PutMapping("/{key}")
    public ResponseEntity<String> put(@PathVariable String key, @RequestBody String payload) {
        MasterResponse response = cacheRouter.routePut(key, payload);
        return toResponseEntity(response);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> delete(@PathVariable String key) {
        MasterResponse response = cacheRouter.routeDelete(key);
        return toResponseEntity(response);
    }

    @GetMapping("/{key}/routing")
    public ResponseEntity<RoutingInfo> routing(@PathVariable String key) {
        RoutingInfo info = cacheRouter.resolveRoutingInfo(key);
        return ResponseEntity.ok(info);
    }

    private ResponseEntity<String> toResponseEntity(MasterResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
