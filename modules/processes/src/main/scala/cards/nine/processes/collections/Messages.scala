package cards.nine.processes.collections

import cards.nine.domain.application.{ BasicCard, FullCard, Package }
import org.joda.time.DateTime

package messages {

  case class SharedCollectionData(
    publicIdentifier: String,
    userId: Option[Long],
    publishedOn: DateTime,
    author: String,
    name: String,
    views: Option[Int] = None,
    category: String,
    icon: String,
    community: Boolean,
    packages: List[Package]
  )

  case class SharedCollectionWithAppsInfo[A](
    collection: SharedCollection,
    appsInfo: List[A]
  )

  case class SharedCollection(
    publicIdentifier: String,
    publishedOn: DateTime,
    author: String,
    name: String,
    views: Int,
    category: String,
    icon: String,
    community: Boolean,
    owned: Boolean,
    packages: List[Package],
    subscriptionsCount: Option[Long] = None
  )

  case class SharedCollectionUpdateInfo(
    title: String
  )

  case class CreateCollectionRequest(
    collection: SharedCollectionData
  )

  case class PackagesStats(added: Int, removed: Option[Int] = None)

  case class CreateOrUpdateCollectionResponse(
    publicIdentifier: String,
    packagesStats: PackagesStats
  )

  case class GetCollectionByPublicIdentifierResponse(data: SharedCollectionWithAppsInfo[FullCard])

  case class GetSubscriptionsByUserResponse(subscriptions: List[String])

  case class IncreaseViewsCountByOneResponse(publicIdentifier: String)

  case class SubscribeResponse()

  case class UnsubscribeResponse()

  case class GetCollectionsResponse(collections: List[SharedCollectionWithAppsInfo[BasicCard]])

}