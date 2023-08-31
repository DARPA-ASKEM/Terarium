package software.uncharted.pantera.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import software.uncharted.pantera.model.User;

import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurrentUserService {

  private final UserService userService;
  private final AdminClientService adminClientService;

  public Jwt getToken() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (Jwt) (authentication.getPrincipal());
  }

  public User get() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication.getPrincipal() instanceof User) {
      return (User) authentication.getPrincipal();
    } else {
      final Jwt jwt = (Jwt) (authentication.getPrincipal());
      final User user = adminClientService.getUserFromJwt(jwt)
        .setAuthorities(authentication.getAuthorities().stream().map(a -> new SimpleGrantedAuthority(a.getAuthority())).collect(Collectors.toList()));

      final User storedUser = userService.getById(user.getId());
      user.merge(storedUser);
      return user;
    }
  }
}
