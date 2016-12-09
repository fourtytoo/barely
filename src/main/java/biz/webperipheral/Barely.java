package biz.webperipheral;

import java.io.File;
import java.io.IOException;
import java.lang.RuntimeException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;



/**
 * Just like SimpleScriptContext but with a copy constructor
 */
class MyScriptContext extends SimpleScriptContext {
  public MyScriptContext(ScriptContext ctx) {
    super();
    setBindings(ctx.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.ENGINE_SCOPE);
  }
}



/**
 * The servlet implementing the JS template pages.
 * <p>
 * The algorithm is mostly implemented in the expandHTML() methods.
 * The DOM of the HTML template is parsed with the help of Jsoup and
 * then fed to expandHTML().
 * <p>
 * The implementation of expandHTML() strives to be functional rather
 * than imperative/OO.  It recursively traverses the DOM and produces
 * another DOM without modifying the original.  Along the way it runs
 * all the JS it comes across.  The new DOM contains no more
 * server-side JS and all the dollar-constructs are evaluated and
 * replaced.  The expanded DOM is then rendered in the HTTP response.
 *
 * <p>
 * 
 * Both <code>data-if</code> and <code>data-for-*</code> attributes
 * can appear in an HTML element at the same time. The
 * <code>data-if</code> part is evaluated first, the
 * <code>data-for</code> later.  That is, the variable bound by
 * <code>data-for</code> part is not part of the <code>data-if</code>
 * scope.
 * 
 * <p>
 * The following
 * <div>
 * <code>
 * &lt;div data-if="foo" data-for-var="f()"&gt;var=${var}&lt;/div&gt;
 * </code>
 * </div>
 * is roughly equivalent to
 * <div>
 * <code>
 * &lt;div data-if="foo"&gt;&lt;div data-for-var="f()"&gt;var=${var}&lt;/div&gt;&lt;/div&gt;
 * </code>
 * </div>
 * 
 * <p>
 * The following is an error
 * <div>
 * <code>
 * &lt;div data-if="var &gt; 0" data-for-var="f()"&gt;var=${var}&lt;/div&gt;
 * </code>
 * </div>
 * 
 * In this example <code>var</code> is not defined yet and cannot be
 * part of the <code>data-if</code> condition.  Bottom line: if you
 * need to filter repeated items, do it in you JS code, not in your
 * HTML template.
 * 
 * <p>
 * The functional aspects of the code are the following: in place
 * modification of objects avoided (mostly); traversal of collections
 * with streams; lambdas where possible.
 *
 * <p>
 * Although not more performant, the functional implementation is
 * easier to reason about and is a natural fit for recursive
 * applications such as this one.
 * 
 * @see #expandHTML(Node, ScriptContext)
 */
public class Barely extends HttpServlet
{
  ScriptEngine engine;
  Pattern dollar_pattern;
  
  @Override
  public void init () throws ServletException
  {
    engine = new ScriptEngineManager().getEngineByName("nashorn");
    // the index.html contains stuff that must be specific to rhino:
    // load the compatibility library.
    evalJS("load(\"nashorn:mozilla_compat.js\");");
    dollar_pattern = Pattern.compile("\\$\\{([^{}]+)}");
  }

  /**
   * Execute a piece of JavaScript using the default engine context.
   *
   * @param code	the source code string to be executed
   * @return		whatever object is yielded from the execution of the code
   * @see #evalJS(String, ScriptContext)
   */
  Object evalJS(String code) {
    return evalJS(code, engine.getContext());
  }
  
  /**
   * Execute a piece of JavaScript.
   *
   * @param code	the source code string to be executed
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		whatever object is yielded from the execution of the code
   * @throws RuntimeException if the execution of the JS code failed
   * @see #engine
   */
  Object evalJS(String code, ScriptContext ctx) {
    try {
      System.out.println("Running: " + code);
      Object result = engine.eval(code, ctx);
      System.out.println("result = " + result);
      return result;
    }
    catch (ScriptException ex) {
      throw new RuntimeException("Error in JS code", ex);
    }
  }

  /**
   * Escape a string according to HTML standards, so that the JS code
   * (or the database) doesn't mess the template.  The original string
   * is left untouched.
   *
   * @param str	the source string
   * @return		a new string with the substitutions, if any, applied
   */
  static String escapeText (String str) {
    return StringEscapeUtils.escapeHtml3(str);
  }

  /**
   * Apply all the necessary ${code} substitutions to the contents of a
   * string.  The original string is left untouched.
   *
   * @param str	the source string
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a new string with the substitutions, if any, applied
   * @see #evalJS(String str, ScriptContext ctx)
   * @see #dollar_pattern
   */
  String expandDollarSyntax (String str, ScriptContext ctx) {
    Matcher matcher = dollar_pattern.matcher(str);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String js_code = matcher.group(1);
      matcher.appendReplacement(sb, evalJS(js_code, ctx).toString());
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Apply all the necessary ${code} substitutions to the value of an
   * attribute.  The original attribute is left untouched.
   *
   * @param attribute the source attribute
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a new Attribute with the substitutions, if any, applied
   * @see #expandDollarSyntax(String, ScriptContext)
   */
  Attribute expandAttribute(Attribute attribute, ScriptContext ctx) {
    return new Attribute(attribute.getKey(),
			 escapeText(expandDollarSyntax(attribute.getValue(), ctx)));
  }

  /**
   * Apply all the necessary ${code} substitutions to the children of a
   * Node.  The original node is left untouched.
   *
   * @param node	the source node
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a stream of children nodes with the substitutions, if any, applied
   * @see #expandHTML(Node, ScriptContext)
   */
  Stream<Node> expandChildren(Node node, ScriptContext ctx) {
    return node.childNodes().stream()
      .flatMap(child -> expandHTML(child, ctx));
  }

  /**
   * Find the first attribute matching a predicate.
   *
   * @param attrs	the node attributes
   * @param pred	a predicate that can be applied to an attribute
   * @return		an Attribute object or null, if not found
   */
  static Attribute findAttribute(Attributes attrs, Predicate<Attribute> pred) {
    return attrs.asList().stream()
      .filter(pred)
      .findFirst()
      .orElse(null);
  }

  /**
   * Find the first attribute matching a predicate in node.
   *
   * @param node	the node where to find the attribute
   * @param pred	a predicate that can be applied to an attribute
   * @return		an Attribute object or null, if not found
   * @see #findAttribute(Attributes, Predicate)
   */
  static Attribute findAttribute(Element node, Predicate<Attribute> pred) {
    return findAttribute(node.attributes(), pred);
  }

  /**
   * Return true if attribute is an HTML5 data-* attribute.
   *
   * @param a		the attribute
   * @return		a boolean
   */
  static boolean isDataAttribute(Attribute a) {
    return a.getKey().startsWith("data-");
  }

  /**
   * Create and returns a new instance of Element starting from the
   * node provided.  Attributes are expanded and so are the children.
   * <p>
   * This is an helper function for {@link #expandHTML(Element, ScriptContext)}.
   *
   * @param node	the node to copy/expand
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		an Element object
   * @see #expandHTML(Element, ScriptContext)
   * @see #expandChildren(Node, ScriptContext)
   * @see #expandAttribute(Attribute, ScriptContext)
   */
  Element expandElement(Element node, ScriptContext ctx) {
    Attributes standard_attributes = new Attributes();
    node.attributes().asList().stream()
      .filter(a -> !isDataAttribute(a))
      .map(a -> expandAttribute(a, ctx))
      .forEach(a -> standard_attributes.put(a));
    Element new_node = new Element(node.tag(), node.baseUri(), standard_attributes);
    expandChildren(node, ctx).forEach(child -> new_node.appendChild(child));
    return new_node;
  }

  /**
   * Check whether an Element node contains a server script.
   *
   * @param node	the Element node
   * @return		true if it is a server script
   */
  static boolean isServerScript(Element node) {
    return (node.tagName() == "script" &&
	    node.attr("type").equals("server/javascript"));
  }

  /**
   * Expand a TextNode of a DOM, performing all the necessary
   * dollar-substitutions.  The original node is left untouched.
   *
   * @param node	the source node
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a stream of containing a TextNode with the substitutions applied
   * @see #expandDollarSyntax(String, ScriptContext)
   */
  Stream<Node> expandHTML(TextNode node, ScriptContext ctx) {
    return Stream.of(new TextNode(escapeText(expandDollarSyntax(node.getWholeText(), ctx)),
				  node.baseUri()));
  }
  
  /**
   * Expand Document node of a DOM, performing all the necessary
   * dollar-substitutions on it and all its children.  The original
   * Document is kept untouched.
   *
   * @param node	the root node of the tree
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a Stream of nodes
   * @see #expandChildren(Node, ScriptContext)
   */
  Stream<Node> expandHTML(Document node, ScriptContext ctx) {
    Document new_node = new Document(node.baseUri());
    expandChildren(node, ctx).forEach(child -> new_node.appendChild(child));
    return Stream.of(new_node);
  }

  /**
   * Expand an Elemend node of a DOM, performing all the necessary
   * dollar-substitutions on it and all its children.  The original
   * Element is kept untouched.
   * <p>
   * Both the data-if and data-for-* attributes are handled and they
   * can appear in the same node.  If data-for-* is present, the
   * returned stream may contain multiple elements.  Otherwise just
   * one.
   *
   * @param node	the root node of the tree
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a Stream of nodes
   * @see #expandElement(Element, ScriptContext)
   */
  Stream<Node> expandHTML(Element node, ScriptContext ctx) {
    if (isServerScript(node)) {
      evalJS(node.html(), ctx);
      return Stream.empty();
    } else {
      String data_if = node.attr("data-if");

      if (data_if != null) {
	Object result = evalJS(data_if, ctx);
	if (result instanceof Boolean &&
	    (Boolean)result == false) {
	  return Stream.empty();
	}
      }

      final String data_for = "data-for-";
      Attribute data_for_attribute = findAttribute(node, a -> a.getKey().startsWith(data_for));

      if (data_for_attribute == null) {
	return Stream.of(expandElement(node, ctx));
      } else {
	String name = data_for_attribute.getKey().substring(data_for.length());
	Object result = evalJS(data_for_attribute.getValue(), ctx);
	Stream<Object> values;

	if (result instanceof Collection<?>) {
	  values = ((Collection<Object>)result).stream();
	} else {
	  values = Stream.of(result);
	}
	// duplicate the element for as many result values as returned by the JS code
	return values.map(val -> {
	    MyScriptContext local_ctx = new MyScriptContext(ctx);
	    local_ctx.setAttribute(name, val, ScriptContext.ENGINE_SCOPE);
	    return expandElement(node, local_ctx);
	  });
      }
    }
  }

  /**
   * This method doesn't actually do anything; it dispatches to the
   * expandHTML() method specialised on the specific node.  This
   * method is necessary because Java does not support multi method
   * dispatch.  A Visitor pattern cannot be implemented without
   * changes to the classes of the objects returned by Jsoup.parse().
   *
   * @param node	the root node of the tree
   * @param ctx	a ScriptContext to use to execute the JS code
   * @return		a Stream of nodes (tree themselves)
   */
  Stream<Node> expandHTML(Node node, ScriptContext ctx) {
    if (node instanceof TextNode) {
      return expandHTML((TextNode)node, ctx);
    } else if (node instanceof Document) {
      return expandHTML((Document) node, ctx);
    } else if (node instanceof Element) {
      return expandHTML((Element) node, ctx);
    } else
      return Stream.of(node.clone());
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String pathname = request.getPathTranslated();
    Document doc = Jsoup.parse(new File(pathname), null);
    ScriptContext ctx = new MyScriptContext(engine.getContext());
    ctx.setAttribute("request", request, ScriptContext.ENGINE_SCOPE);
    Node converted_doc = expandHTML(doc, ctx).findFirst().get();
    assert converted_doc instanceof Document;

    // fill the HTTP response
    response.setContentType("text/html");
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().println(((Document) converted_doc).html());
  }
}
