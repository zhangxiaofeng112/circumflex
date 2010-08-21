package ru.circumflex.core

import java.lang.String
import collection.{Iterator, Map}
import collection.mutable.HashMap
import java.util.{ResourceBundle, Locale}
import collection.mutable.ListBuffer
import java.text.MessageFormat

/*!# Messages API

Messages API offer you a convenient way to internationalize your application.

Generally, all strings that should be presented to user are stored in
separate `.properties`-files as suggested by [Java Internationalization][java-i18n].

Circumflex Messages API goes beyound this simple approach and offers
delegating resolving, messages grouping, parameters interpolation and formatting.

  [java-i18n]: http://java.sun.com/javase/technologies/core/basic/intl

`MessageResolver` is responsible for resolving a message by `key`. The locale
is taken from `cx.locale` context variable (see `Context` for more details). If
no such variable found in context, platform's default locale is used.

The default implementation (`msg` method in package `ru.circumflex.core`)
uses `ResourceBundle` with base name `Messages` to lookup messages. You can override
the default implementation by setting `cx.messages` configuration parameter.

You can use `ResourceBundleMessageResolver` to resolve messages from another bundles.

If you need to search messages in different sources, you can use
`DelegatingMessageResolver`: it tries to resolve a message using specified
`resolvers` list, the first resolved message is returned.

Messages can also be formatted. We support both classic `MessageFormat` style
and parameters interpolation (key-value pairs are passed as arguments to `interpolate`
method, each `{key}` in message are replaced by corresponding value).

Circumflex Messages API also allows very robust ranged resolving. The message is searched
using the range of keys, from the most specific to the most general ones: if the message
is not resolved with using given key, then the key is truncated from the left side to
the first dot (`.`) and the message is searched again. For example, if you are looking
for a message with key `com.myapp.model.Account.name.empty` (possibly while performing
domain model validation), then following keys will be used to lookup an appropriate
message:

    com.myapp.model.Account.name.empty
    myapp.model.Account.name.empty
    model.Account.name.empty
    Account.name.empty
    name.empty
    empty


*/
trait MessageResolver extends Map[String, String] {
  def -(key: String): Map[String, String] = this
  def +[B1 >: String](kv: (String, B1)): Map[String, B1] = this

  def get(key: String): Option[String] = resolveRange(key) orElse {
    CX_LOG.debug("Message with key '" + key + "' is missing.")
    None
  }

  protected def resolveRange(key: String): Option[String] = resolve(key) orElse {
    if (!key.contains(".")) None
    else resolveRange(key.substring(key.indexOf(".") + 1))
  }

  protected def resolve(key: String): Option[String]

  def locale: Locale = Circumflex.get("cx.locale") match {
    case Some(l: Locale) => l
    case Some(l: String) => new Locale(l)
    case _ => Locale.getDefault
  }

  def interpolate(key: String, params: Pair[String, Any]*): String =
    params.foldLeft(getOrElse(key, "")) {
      (result, p) => result.replaceAll("\\{" + p._1 + "\\}", p._2.toString)
    }
  def format(key: String, params: Any*): String =
    MessageFormat.format(getOrElse(key, ""), params.toArray)
}

object ResourceBundleCache extends HashMap[(String, Locale), Map[String, String]] {
  override def get(key: (String, Locale)): Option[Map[String, String]] = super.get(key) orElse {
    val m = HashMap[String, String]()
    try {
      val b = ResourceBundle.getBundle(key._1, key._2)
      val e = b.getKeys
      while (e.hasMoreElements) {
        val k = e.nextElement
        m(k) = b.getString(k)
      }
    } catch {
      case e =>
        CX_LOG.error("Could not process message bundle.", e)
    }
    this(key) = m
    Some(m)
  }
}

class ResourceBundleMessageResolver(val bundleName: String) extends MessageResolver {
  def iterator: Iterator[(String, String)] = bundle.iterator
  protected def bundle = ResourceBundleCache(bundleName -> locale)
  protected def resolve(key: String): Option[String] = bundle.get(key)
}

class DelegatingMessageResolver(initialResolvers: MessageResolver*) {
  protected var _resolvers: Seq[MessageResolver] = initialResolvers
  def resolvers = _resolvers
  def addResolver(r: MessageResolver): this.type = {
    _resolvers ++= List(r)
    return this
  }
  def iterator: Iterator[(String, String)] =
    resolvers.map(_.iterator).reduceLeft((a, b) => a ++ b)
  protected def resolve(key: String): Option[String] = {
    resolvers.foreach(r => r.get(key).map(msg => return Some(msg)))
    return None
  }
}

object DefaultMessageResolver extends ResourceBundleMessageResolver("Messages")


