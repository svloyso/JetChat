package api

import controllers.RequestUtils
import play.api.mvc.{AnyContent, Request}

/**
 * @author Alefas
 * @since  15/09/15
 */
object Utils {
  def callbackUrl(integrationId: String, redirectUrl: Option[String])(implicit request: Request[AnyContent]): String =
    controllers.routes.IntegrationAuth.callback(integrationId, None, None, redirectUrl).absoluteURL(RequestUtils.secure)
}


