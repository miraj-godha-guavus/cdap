package com.continuuity.security.server;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.continuuity.common.io.Codec;
import com.continuuity.security.auth.AccessToken;
import com.continuuity.security.auth.AccessTokenIdentifier;
import com.continuuity.security.auth.TokenManager;
import com.google.common.base.Charsets;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * Generate and grant access token to authorized users.
 */
@Path("/")
public class GrantAccessTokenHandler extends AbstractHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GrantAccessTokenHandler.class);
  private final TokenManager tokenManager;
  private final Codec<AccessToken> tokenCodec;
  private final CConfiguration cConf;

  @Inject
  public GrantAccessTokenHandler(TokenManager tokenManager,
                                 Codec<AccessToken> tokenCodec,
                                 CConfiguration cConfiguration) {
    this.tokenManager = tokenManager;
    this.tokenCodec = tokenCodec;
    this.cConf = cConfiguration;
  }

  @Override
  protected void doStart() {
    tokenManager.start();
  }

  @Override
  protected void doStop() {
    tokenManager.stop();
  }

  public static final class Paths {
    public static final String GET_TOKEN = "token";
    public static final String GET_EXTENDED_TOKEN = "extendedtoken";
  }

  @Path(Paths.GET_TOKEN)
  @GET
  @Produces("application/json")
  public Response token(@Context HttpServletRequest request, @Context HttpServletResponse response)
      throws IOException, ServletException {
    this.handle(Paths.GET_TOKEN, Request.getRequest(request), request, response);
    return Response.status(200).build();
  }

  @Path(Paths.GET_EXTENDED_TOKEN)
  @GET
  @Produces("application/json")
  public Response extendedToken(@Context HttpServletRequest request, @Context HttpServletResponse response)
    throws IOException, ServletException {
    this.handle(Paths.GET_EXTENDED_TOKEN, Request.getRequest(request), request, response);
    return Response.status(200).build();
  }


  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException {

    String username = request.getUserPrincipal().getName();
    List<String> userGroups = Collections.emptyList();

    long tokenValidity;
    if (target.equals(Paths.GET_TOKEN)) {
      tokenValidity = cConf.getLong(Constants.Security.TOKEN_EXPIRATION);
    } else if (target.equals(Paths.GET_EXTENDED_TOKEN)) {
      tokenValidity = cConf.getLong(Constants.Security.EXTENDED_TOKEN_EXPIRATION);
    } else {
      throw new ServletException("Unknown path");
    }

    long issueTime = System.currentTimeMillis();
    long expireTime = issueTime + tokenValidity;
    // Create and sign a new AccessTokenIdentifier to generate the AccessToken.
    AccessTokenIdentifier tokenIdentifier = new AccessTokenIdentifier(username, userGroups, issueTime, expireTime);
    AccessToken token = tokenManager.signIdentifier(tokenIdentifier);
    LOG.debug("Issued token for user {}", username);

    // Set response headers
    response.setContentType("application/json;charset=UTF-8");
    response.addHeader("Cache-Control", "no-store");
    response.addHeader("Pragma", "no-cache");

    // Set response body
    JsonObject json = new JsonObject();
    byte[] encodedIdentifier = Base64.encodeBase64(tokenCodec.encode(token));
    json.addProperty(ExternalAuthenticationServer.ResponseFields.ACCESS_TOKEN,
                     new String(encodedIdentifier, Charsets.UTF_8));
    json.addProperty(ExternalAuthenticationServer.ResponseFields.TOKEN_TYPE,
                     ExternalAuthenticationServer.ResponseFields.TOKEN_TYPE_BODY);
    json.addProperty(ExternalAuthenticationServer.ResponseFields.EXPIRES_IN, tokenValidity / 1000);

    response.getOutputStream().print(json.toString());
    response.setStatus(HttpServletResponse.SC_OK);
    baseRequest.setHandled(true);
  }
}
