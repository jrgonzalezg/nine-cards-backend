package com.fortysevendeg.ninecards.googleplay.api

import spray.httpx.marshalling.Marshaller
import spray.httpx.marshalling.ToResponseMarshaller
import spray.testkit.{RouteTest, TestFrameworkInterface}
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._
import com.fortysevendeg.ninecards.googleplay.domain.Domain._

import cats.data.Xor
import cats.syntax.option._

import org.scalacheck._
import org.scalacheck.Prop._
import org.scalacheck.Shapeless._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary._

import com.fortysevendeg.ninecards.googleplay.service.GooglePlayService._

import scalaz.concurrent.Task

trait ScalaCheckRouteTest extends RouteTest with TestFrameworkInterface {
  def failTest(msg: String): Nothing = throw new RuntimeException(msg)
}

object NineCardsGooglePlayApiProperties extends Properties("Nine Cards Google Play API") with ScalaCheckRouteTest {

  val requestHeaders = List(
    RawHeader("X-Android-ID", "androidId"),
    RawHeader("X-Google-Play-Token", "googlePlayToken"),
    RawHeader("X-Android-Market-Localization", "es-ES")
  )

  // we want a slightly different generator to the one that would be automatically generated - no empty string.
  implicit val arbPackage: Arbitrary[Package] = Arbitrary(nonEmptyListOf(alphaNumChar).map(chars => Package(chars.mkString)))

  // TODO pull this out somewhere else
  // A generator which returns a map of A->B, a list of As that are in the map, and a list of As that are not
  def genPick[A, B](implicit aa: Arbitrary[A], ab: Arbitrary[B]): Gen[(Map[A, B], List[A], List[A])] = for {
    pairs <- arbitrary[Map[A, B]]
    keys = pairs.keySet
    validPicks <- someOf(keys)
    anotherList <- listOf(arbitrary[A])
    invalidPicks = anotherList.filterNot(i => keys.contains(i))
  } yield (pairs, validPicks.toList, invalidPicks)

  property("returns the correct package name for a Google Play Store app") = forAll { (pkg: Package, item: Item) =>

    def requestPackage(params: GoogleAuthParams): Package => Task[Option[Item]] = { p =>
      if(p == pkg) {
        Task.now(Some(item))
      } else {
        Task.now(None)
      }
    }

    val route = new NineCardsGooglePlayApi {
      override def actorRefFactory = system
    }.googlePlayApiRoute(requestPackage _)

    Get(s"/googleplay/package/${pkg.value}") ~> addHeaders(requestHeaders) ~> route ~> check {
      val response = responseAs[String]

      (status ?= OK) && (decode[Item](response) ?= Xor.right(item))
    }
  }

  property("fails with an Internal Server Error when the package is not known") = forAll {(unknownPackage: Package, wrongItem: Item) =>

    def requestPackage(params: GoogleAuthParams): Package => Task[Option[Item]] = { p =>
      if(p == unknownPackage) {
        Task.now(None)
      } else {
        Task.now(Some(wrongItem))
      }
    }

    val route = new NineCardsGooglePlayApi {
      override def actorRefFactory = system
    }.googlePlayApiRoute(requestPackage _)

    Get(s"/googleplay/package/${unknownPackage.value}") ~> addHeaders(requestHeaders) ~> route ~> check {
      status ?= InternalServerError
    }
  }

  property("gives the package details for the known packages and highlights the errors") = forAll(genPick[Package, Item]) { (data: (Map[Package, Item], List[Package], List[Package])) =>

    val (database, succs, errs) = data

    //order doesn't matter
    val errors = errs.map(_.value).toSet
    val items = succs.map(i => database(i)).toSet

    def requestPackage(params: GoogleAuthParams): Package => Task[Option[Item]] = { p =>
      Task.now(database.get(p))
    }

    val route = new NineCardsGooglePlayApi {
      override def actorRefFactory = system
    }.googlePlayApiRoute(requestPackage _)

    val allPackages = (succs ++ errs).map(_.value)

    Post("/googleplay/packages/detailed", PackageListRequest(allPackages).asJson.noSpaces) ~> addHeaders(requestHeaders) ~> route ~> check {

      val response = responseAs[String]
      val decoded = decode[PackageDetails](response).getOrElse(throw new RuntimeException(s"Unable to parse response [$response]"))

      (status ?= OK) &&
      (decoded.errors.toSet ?= errors) &&
      (decoded.items.toSet ?= items)
    }
  }
}
