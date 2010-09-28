package com.springsource.greenhouse.connect;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.encrypt.StringEncryptor;
import org.springframework.social.facebook.FacebookOperations;
import org.springframework.social.facebook.FacebookTemplate;
import org.springframework.social.linkedin.LinkedInOperations;
import org.springframework.social.linkedin.LinkedInTemplate;
import org.springframework.social.twitter.TwitterOperations;
import org.springframework.social.twitter.TwitterTemplate;

import com.springsource.greenhouse.account.AccountMapper;

//TODO this is disabled because it's needed in root-context yet scanned by app-servlet-context. not very clean: revisit this.
//@Repository
public class JdbcAccountProviderFactory implements AccountProviderFactory {
	
	private final JdbcTemplate jdbcTemplate;

	private final AccountMapper accountMapper;

	private final StringEncryptor encryptor;
	
	@Autowired
	public JdbcAccountProviderFactory(JdbcTemplate jdbcTemplate, StringEncryptor encryptor, AccountMapper accountMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.accountMapper = accountMapper;
		this.encryptor = encryptor;
	}

	@SuppressWarnings("unchecked")
	public <A> AccountProvider<A> getAccountProvider(Class<A> apiType) {
		if (FacebookOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<FacebookOperations>(getParameters("facebook"), jdbcTemplate, encryptor, accountMapper) {
				public FacebookOperations createApi(OAuthToken accessToken) {
					if (accessToken == null) {
						throw new IllegalStateException("Cannot access Facebook without an access token");
					}
					return new FacebookTemplate(accessToken.getValue());
				}
			};
		} else if (TwitterOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<TwitterOperations>(getParameters("twitter"), jdbcTemplate, encryptor, accountMapper) {
				public TwitterOperations createApi(OAuthToken accessToken) {
					return accessToken != null ? new TwitterTemplate(getApiKey(), getSecret(), accessToken.getValue(), accessToken.getSecret()) : new TwitterTemplate();
				}
			};			
		} else if (LinkedInOperations.class.equals(apiType)) {
			return (AccountProvider<A>) new JdbcAccountProvider<LinkedInOperations>(getParameters("linkedin"),
					jdbcTemplate, encryptor, accountMapper) {
				public LinkedInOperations createApi(OAuthToken accessToken) {
					if (accessToken == null) {
						throw new IllegalStateException("Cannot access LinkedIn without an access token");
					}
					return new LinkedInTemplate(getApiKey(), getSecret(), accessToken.getValue(),
							accessToken.getSecret());
				}
			};
		} else {
			throw new IllegalArgumentException("Not a supported apiType " + apiType);
		}
	}
	
	private AccountProviderParameters getParameters(final String provider) {
		return jdbcTemplate.queryForObject(SELECT_ACCOUNT_PROVIDER_BY_NAME, new RowMapper<AccountProviderParameters>() {
			public AccountProviderParameters mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new AccountProviderParameters(provider, encryptor.decrypt(rs.getString("apiKey")), encryptor.decrypt(rs.getString("secret")), rs.getLong("appId"), rs.getString("requestTokenUrl"), rs.getString("authorizeUrl"), rs.getString("callbackUrl"), rs.getString("accessTokenUrl"));
			}
		}, provider);
	}

	private static final String SELECT_ACCOUNT_PROVIDER_BY_NAME = "select apiKey, secret, appId, requestTokenUrl, authorizeUrl, callbackUrl, accessTokenUrl from AccountProvider where name = ?";

}