package controllers

import play.api.mvc.{RequestHeader, AnyContent, Request}

object RequestUtils {
  def secure(implicit request: RequestHeader): Boolean = {
    request.secure || request.headers.get("x-forwarded-proto").getOrElse("").contains("https")
  }

}
