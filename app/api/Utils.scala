package api

import play.api.mvc.{RequestHeader, AnyContent, Request}

/**
 * @author Alefas
 * @since  15/09/15
 */
object Utils {
  def callbackUrl(integrationId: String, redirectUrl: Option[String])(implicit requestHeader: RequestHeader): String =
    controllers.routes.IntegrationAuth.callback(integrationId, None, None, redirectUrl).absoluteURL()
}
