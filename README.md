# Stream Gatherers: Processing Blog Content with JDK 24

![Java Version](https://img.shields.io/badge/Java-24-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## Transform Your Java Data Processing

Welcome to the Stream Gatherers project! This repository showcases one of JDK 24's most powerful new features: Stream Gatherers (JEP 485). If you've ever struggled with complex stream operations or nested collectors, you'll appreciate how Gatherers simplify data transformation tasks while maintaining readability and performance.

This project demonstrates practical applications of Stream Gatherers for blog content management, with reusable components that you can adapt for your own applications.

## What Makes Stream Gatherers Special?

Stream Gatherers fill the gap between intermediate and terminal Stream operations by allowing you to create custom, reusable stream transformations. They provide:

- More expressiveness than standard stream operations
- Better encapsulation of complex multi-step processing
- Support for stateful operations without external variables
- Simplified thread-safety for parallel streams

## Project Requirements

- JDK 24 or later (using preview features)
- Maven for dependency management
- Basic understanding of Java Streams API

## Dependencies

This project intentionally has minimal dependencies:

- Java 24 (for Stream Gatherers support)
- Maven for project management

The `pom.xml` specifies Java 24 as the source and target versions:

```xml
<properties>
    <maven.compiler.source>24</maven.compiler.source>
    <maven.compiler.target>24</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

## Getting Started

After cloning the repository, make sure you have JDK 24 installed and correctly configured:

```bash
java --version
# Should show Java 24 or higher
```

To build and run the project:

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="dev.danvega.Main"
```

## Core Components

The project consists of three main classes:

1. **BlogPost** - A record representing a blog article:
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

2. **BlogGatherers** - Custom gatherers for blog data processing:
    - `groupByWithLimit` - Groups posts by a key with limit per group
    - `relatedPosts` - Finds posts similar to a target post
    - `extractTags` - Extracts and counts hashtags from content
    - `calculateReadingTimes` - Estimates reading time for posts
    - `popularAuthors` - Identifies the most prolific authors
    - `monthlyArchive` - Creates a chronological archive view
    - `recentPostsByCategory` - Shows recent posts by category

3. **Main** - Demonstrates gatherer usage with sample data

## How Stream Gatherers Work

Let's examine the core functionality by looking at the `groupByWithLimit` gatherer:

```java
public static <K> Gatherer<BlogPost, Map<K, List<BlogPost>>, Map.Entry<K, List<BlogPost>>> groupByWithLimit(
        Function<? super BlogPost, ? extends K> keyExtractor,
        int limit,
        Comparator<? super BlogPost> comparator) {

    return Gatherer.of(
        // Initialize state
        HashMap<K, List<BlogPost>>::new,

        // Process each blog post
        (map, post, downstream) -> {
            K key = keyExtractor.apply(post);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(post);
            return true;
        },

        // Combiner for parallel streams
        (map1, map2) -> map1,

        // Emit results when complete
        (map, downstream) -> {
            map.forEach((key, posts) -> {
                List<BlogPost> limitedPosts = posts.stream()
                    .sorted(comparator)
                    .limit(limit)
                    .toList();
                downstream.push(Map.entry(key, limitedPosts));
            });
        }
    );
}
```

This gatherer:
1. Groups posts by a key (like category)
2. Sorts each group using a comparator
3. Limits each group to a specified number of posts
4. Emits each group as a Map.Entry

## Usage Examples

### Grouping Posts by Category

Before JDK 24, you might use nested collectors:

```java
Map<String, List<BlogPost>> recentPostsByCategory = posts.stream()
    .collect(Collectors.groupingBy(
        BlogPost::category,
        Collectors.collectingAndThen(
            Collectors.toList(),
            categoryPosts -> categoryPosts.stream()
                .sorted(Comparator.comparing(BlogPost::publishedDate).reversed())
                .limit(3)
                .toList()
        )
    ));
```

With Stream Gatherers, this becomes:

```java
Map<String, List<BlogPost>> recentPostsByCategory = posts.stream()
    .gather(BlogGatherers.groupByWithLimit(
        BlogPost::category,    // Group by category
        3,                     // Show only 3 recent posts per category
        Comparator.comparing(BlogPost::publishedDate).reversed() // Newest first
    ))
    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
```

### Finding Related Posts

You can use the `relatedPosts` gatherer to find similar posts:

```java
BlogPost targetPost = posts.getFirst();
List<BlogPost> relatedPosts = posts.stream()
    .gather(BlogGatherers.relatedPosts(targetPost, 5))
    .findFirst()
    .orElse(List.of());
```

### Creating a Monthly Archive

Generate a reverse-chronological archive of posts:

```java
Map<YearMonth, List<BlogPost>> archive = posts.stream()
    .gather(BlogGatherers.monthlyArchive())
    .findFirst()
    .orElse(Map.of());
```

## Standard Gatherers

JDK 24 provides several built-in gatherers in `java.util.stream.Gatherers`:

- `windowFixed` - Groups elements into fixed-size windows
- `windowSliding` - Creates overlapping windows of elements
- `fold` - Folds elements into a single result
- `scan` - Produces intermediate results of a fold
- `mapConcurrent` - Processes elements concurrently

The `Main` class demonstrates these standard gatherers in addition to our custom ones.

## Advanced Features

### Parallel Processing

Stream Gatherers are designed to work with parallel streams. The combiner function helps merge results when processing in parallel:

```java
List<BlogPost> processedPosts = posts.parallelStream()
    .gather(myCustomGatherer)
    .toList();
```

### Stateful Processing

Unlike standard intermediate operations, gatherers can maintain state across elements, making them ideal for:

- Running calculations (like moving averages)
- Building maps or complex data structures
- Implementing custom window functions

## Best Practices

When creating your own gatherers:

1. Make them reusable and configurable with parameters
2. Ensure thread safety for parallel streams
3. Use descriptive names that indicate their purpose
4. Document any assumptions about input data
5. Consider providing sequential and parallel versions for complex operations

## Conclusion

Stream Gatherers represent a significant advancement in Java's functional programming capabilities. They enable cleaner, more expressive data processing without sacrificing performance.

This project demonstrates how you can leverage Stream Gatherers to implement common blog content operations with less code and greater clarity. Whether you're building a content management system, data pipeline, or any application that processes streams of data, Stream Gatherers provide a powerful new tool in your Java development arsenal.

Give them a try and see how they can transform your approach to data processing!