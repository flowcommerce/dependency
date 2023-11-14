package cache

import db.{Authorization, ResolversDao}
import io.flow.dependency.v0.models.Resolver
import io.flow.util.CacheWithFallbackToStaleData

@javax.inject.Singleton
case class ResolversCache @javax.inject.Inject() (
  resolversDao: ResolversDao,
) extends CacheWithFallbackToStaleData[String, Option[Resolver]] {

  override def refresh(resolverId: String): Option[Resolver] = {
    resolversDao.findById(Authorization.All, resolverId)
  }

  def findByResolverId(resolverId: String): Option[Resolver] = {
    get(resolverId)
  }
}
