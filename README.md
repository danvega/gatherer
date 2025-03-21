# Stream Gatherers in JDK 24

![Java Version](https://img.shields.io/badge/Java-24-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## Transforming Blog Content Processing with Custom Stream Operations

Welcome to the Stream Gatherers repository! This project demonstrates one of the most powerful additions to JDK 24: Stream Gatherers (JEP 485). If you've ever found yourself wrestling with complex nested collectors or multi-step stream operations, you're in for a treat.

This repository showcases how Stream Gatherers can dramatically simplify common data processing tasks in blog applications, with a focus on implementing a "Recent Posts By Category" feature that every content site needs.

## What Are Stream Gatherers?

Stream Gatherers bridge the gap between intermediate and terminal Stream operations, allowing you to create custom, reusable stream transformations. They enhance Java's expressiveness by enabling you to encapsulate complex processing logic in a clean, readable way.

## Project Requirements

- JDK 24 or later (released March 2025)
- Basic understanding of Java Streams API
- Familiarity with functional programming concepts

## Dependencies

This project is deliberately lightweight with minimal dependencies:

- JDK 24 (core functionality)
- No external libraries needed!

## Getting Started

After cloning the repository, make sure you have JDK 24 installed on your system. You can verify your Java version by running:

```bash
java --version
```

If you need JDK 24, download it from the official OpenJDK website.

## How to Run the Application

The main class demonstrates three different approaches to implement a "Recent Posts By Category" feature:

```bash
# Compile the project
javac dev/danvega/Main.java

# Run the application
java dev.danvega.Main
```

The application will display the results of each approach, making it easy to compare them.

## Understanding the Code Structure

The repository contains a simple yet powerful example that demonstrates:

1. The traditional approach using nested collectors
2. An alternative approach using map-then-transform
3. The new Stream Gatherers approach (JDK 24)

The core of the project is centered around the `BlogPost` record:

```java
public record BlogPost(
    Long id,
    String title,
    String author,
    String content,
    String category,
    LocalDateTime publishedDate
) {}
```

## The Power of Stream Gatherers

Here's where things get interesting. The custom `groupByWithLimit` gatherer demonstrates Stream Gatherers in action:

```java
public static <T, K> Gatherer<T, Map<K, List<T>>, Map.Entry<K, List<T>>> groupByWithLimit(
        Function<? super T, ? extends K> keyExtractor,
        int limit,
        Comparator<? super T> comparator) {

    return Gatherer.of(
            // Initialize with an empty map
            HashMap<K, List<T>>::new,

            // Process each element
            (map, element, downstream) -> {
                K key = keyExtractor.apply(element);
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(element);
                return true;
            },

            // Combiner for parallel streams
            (map1, map2) -> map1,

            // Emit results after processing
            (map, downstream) -> {
                map.forEach((key, values) -> {
                    List<T> limitedValues = values.stream()
                            .sorted(comparator)
                            .limit(limit)
                            .toList();
                    downstream.push(Map.entry(key, limitedValues));
                });
            }
    );
}
```

Using this gatherer simplifies our code dramatically:

```java
Map<String, List<BlogPost>> recentPostsByCategory = posts.stream()
        .gather(groupByWithLimit(
                BlogPost::category,    // Group by category
                3,                     // Show only 3 recent posts per category
                Comparator.comparing(BlogPost::publishedDate).reversed() // Newest first
        ))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
```

## Key Components of Stream Gatherers

Stream Gatherers consist of four main components:

1. **Initializer**: Creates the initial state (like our `HashMap`)
2. **Integrator**: Processes each element and updates the state
3. **Combiner**: Combines states when processing in parallel
4. **Finisher**: Processes the final state and emits results

This structure allows for creating powerful, reusable stream operations that weren't possible before.

## Practical Applications

Beyond the "Recent Posts By Category" example, Stream Gatherers can be used for:

- Sliding window analysis of time-based content
- Word frequency counting in blog posts
- Complex pagination with dynamic page sizes
- Content sentiment analysis in a streaming fashion

## Performance Considerations

Stream Gatherers are designed with performance in mind:

- They support parallel streams out of the box
- The combiner function allows efficient parallel processing
- They can often eliminate intermediate collections and multiple stream pipelines
- Processing happens in a single pass through the data

## Conclusion

Stream Gatherers in JDK 24 represent a significant enhancement to Java's functional programming capabilities. They allow you to write cleaner, more expressive code for complex data processing tasks.

This repository provides a practical introduction to Stream Gatherers through a real-world blog content processing example. By comparing traditional approaches with the new Stream Gatherers API, you'll quickly see how this feature can transform your Java code.

Happy gathering!