package lib

import cats.effect.IO
import com.google.inject.{AbstractModule, TypeLiteral}
import io.flow.lib.dependency.clients.DependencyProjects

class DependencyProjectsModule extends AbstractModule {
  override def configure(): Unit = {
    bind(new TypeLiteral[DependencyProjects[IO]]() {})
      .to(classOf[DaoBasedDependencyProjects])
  }
}
