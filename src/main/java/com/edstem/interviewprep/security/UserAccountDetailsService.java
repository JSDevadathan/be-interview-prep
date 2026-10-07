package com.edstem.interviewprep.security;

import com.edstem.interviewprep.entity.UserAccount;
import com.edstem.interviewprep.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public UserAccountDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userAccountRepository.findByUsername(UserAccount.normalizeUsername(username))
                .map(UserAccountDetailsService::toUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User with username %s was not found".formatted(username)));
    }

    private static UserDetails toUserDetails(UserAccount account) {
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .build();
    }
}
