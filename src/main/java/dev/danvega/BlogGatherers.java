package dev.danvega;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Gatherer;

/**
 * Blog-specific gatherers for processing BlogPost streams.
 */
public final class BlogGatherers {

    // This class is not meant to be instantiated
    private BlogGatherers() {}

    /**
     * Returns a gatherer that groups blog posts by a provided key and limits
     * each group to the specified number of posts, sorted by the given comparator.
     *
     * @param keyExtractor function to extract the grouping key from each blog post
     * @param limit maximum number of posts to keep per group
     * @param comparator comparator for sorting posts within each group
     * @param <K> the type of keys for grouping
     * @return a gatherer that produces map entries with keys and limited lists of posts
     */
    public static <K> Gatherer<BlogPost, Map<K, List<BlogPost>>, Map.Entry<K, List<BlogPost>>> groupByWithLimit(
            Function<? super BlogPost, ? extends K> keyExtractor,
            int limit,
            Comparator<? super BlogPost> comparator) {

        return Gatherer.of(
                // Initialize with an empty map to store our grouped items
                HashMap<K, List<BlogPost>>::new,

                // Process each blog post
                (map, post, downstream) -> {
                    // Get the key for this blog post (e.g., the category)
                    K key = keyExtractor.apply(post);

                    // Add this post to its group (creating the group if needed)
                    map.computeIfAbsent(key, k -> new ArrayList<>()).add(post);

                    // Continue processing the stream
                    return true;
                },

                // Combiner for parallel streams - just use the first map in this simple case
                (map1, map2) -> map1,

                // When all posts have been processed, emit the results
                (map, downstream) -> {
                    map.forEach((key, posts) -> {
                        // Sort the posts and limit to the specified number
                        List<BlogPost> limitedPosts = posts.stream()
                                .sorted(comparator)
                                .limit(limit)
                                .toList();

                        // Emit a Map.Entry with the key and limited posts
                        downstream.push(Map.entry(key, limitedPosts));
                    });
                }
        );
    }

    /**
     * Returns a gatherer that finds posts related to the target post
     * based on category and content similarity.
     */
    public static Gatherer<BlogPost, ?, List<BlogPost>> relatedPosts(BlogPost targetPost, int limit) {
        return Gatherer.ofSequential(
                () -> new HashMap<String, List<BlogPost>>(),
                (Map<String, List<BlogPost>> map, BlogPost post, Gatherer.Downstream<? super List<BlogPost>> downstream) -> {
                    // Don't include the target post itself
                    if (!post.id().equals(targetPost.id())) {
                        map.computeIfAbsent(post.category(), k -> new ArrayList<>()).add(post);
                    }
                    return true;
                },
                (Map<String, List<BlogPost>> map, Gatherer.Downstream<? super List<BlogPost>> downstream) -> {
                    // Get posts from the same category
                    List<BlogPost> sameCategoryPosts = map.getOrDefault(targetPost.category(), List.of());

                    // Calculate similarity score and find most similar posts
                    List<BlogPost> relatedPosts = sameCategoryPosts.stream()
                            .map(post -> Map.entry(post, calculateSimilarity(targetPost, post)))
                            .sorted(Map.Entry.<BlogPost, Double>comparingByValue().reversed())
                            .limit(limit)
                            .map(Map.Entry::getKey)
                            .toList();

                    downstream.push(relatedPosts);
                }
        );
    }

    // Helper method for calculating similarity between posts
    // This could be as complex as you want it to be, mine is pretty simple
    private static double calculateSimilarity(BlogPost post1, BlogPost post2) {
        Set<String> words1 = Set.of(post1.title().toLowerCase().split("\\W+"));
        Set<String> words2 = Set.of(post2.title().toLowerCase().split("\\W+"));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        return (double) intersection.size() / Math.max(words1.size(), words2.size());
    }

    /**
     * Returns a gatherer that extracts and counts hashtags from blog post content.
     */
    public static Gatherer<BlogPost, ?, Map<String, Integer>> extractTags() {
        return Gatherer.ofSequential(
                () -> new HashMap<String, Integer>(),
                (Map<String, Integer> tagCounts, BlogPost post, Gatherer.Downstream<? super Map<String, Integer>> downstream) -> {
                    // Extract hashtags from content
                    String content = post.content().toLowerCase();
                    Pattern pattern = Pattern.compile("#(\\w+)");
                    Matcher matcher = pattern.matcher(content);

                    while (matcher.find()) {
                        String tag = matcher.group(1);
                        tagCounts.merge(tag, 1, Integer::sum);
                    }

                    return true;
                },
                (Map<String, Integer> tagCounts, Gatherer.Downstream<? super Map<String, Integer>> downstream) -> {
                    downstream.push(tagCounts);
                }
        );
    }

    /**
     * Returns a gatherer that calculates estimated reading times for blog posts.
     */
    public static Gatherer<BlogPost, ?, Map<Long, Duration>> calculateReadingTimes() {
        // Assume average reading speed of 200 words per minute
        final int WORDS_PER_MINUTE = 200;

        return Gatherer.ofSequential(
                () -> new HashMap<Long, Duration>(),
                (Map<Long, Duration> readingTimes, BlogPost post, Gatherer.Downstream<? super Map<Long, Duration>> downstream) -> {
                    String content = post.content();
                    int wordCount = content.split("\\s+").length;

                    // Calculate reading time in minutes
                    double minutes = (double) wordCount / WORDS_PER_MINUTE;

                    // Convert to Duration
                    Duration readingTime = Duration.ofSeconds(Math.round(minutes * 60));
                    readingTimes.put(post.id(), readingTime);

                    return true;
                },
                (Map<Long, Duration> readingTimes, Gatherer.Downstream<? super Map<Long, Duration>> downstream) -> {
                    downstream.push(readingTimes);
                }
        );
    }

    /**
     * Returns a gatherer that finds the most prolific authors.
     */
    public static Gatherer<BlogPost, ?, List<Map.Entry<String, Integer>>> popularAuthors(int limit) {
        return Gatherer.ofSequential(
                () -> new HashMap<String, Integer>(),
                (Map<String, Integer> authorCounts, BlogPost post, Gatherer.Downstream<? super List<Map.Entry<String, Integer>>> downstream) -> {
                    authorCounts.merge(post.author(), 1, Integer::sum);
                    return true;
                },
                (Map<String, Integer> authorCounts, Gatherer.Downstream<? super List<Map.Entry<String, Integer>>> downstream) -> {
                    List<Map.Entry<String, Integer>> topAuthors = authorCounts.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(limit)
                            .toList();

                    downstream.push(topAuthors);
                }
        );
    }

    /**
     * Returns a gatherer that groups posts by month for an archive view.
     */
    public static Gatherer<BlogPost, ?, Map<YearMonth, List<BlogPost>>> monthlyArchive() {
        return Gatherer.ofSequential(
                () -> new TreeMap<YearMonth, List<BlogPost>>(Comparator.reverseOrder()),
                (Map<YearMonth, List<BlogPost>> archive, BlogPost post, Gatherer.Downstream<? super Map<YearMonth, List<BlogPost>>> downstream) -> {
                    LocalDateTime publishDate = post.publishedDate();
                    YearMonth yearMonth = YearMonth.from(publishDate);

                    archive.computeIfAbsent(yearMonth, k -> new ArrayList<>()).add(post);
                    return true;
                },
                (Map<YearMonth, List<BlogPost>> archive, Gatherer.Downstream<? super Map<YearMonth, List<BlogPost>>> downstream) -> {
                    // Sort posts within each month by publish date (newest first)
                    archive.forEach((month, posts) ->
                            posts.sort(Comparator.comparing(BlogPost::publishedDate).reversed()));

                    downstream.push(archive);
                }
        );
    }

    /**
     * Returns a gatherer specific for blog posts that groups them by category
     * and limits each category to the most recent posts.
     *
     * @param limit maximum number of posts to keep per category
     * @return a gatherer that produces map entries with categories and limited lists of posts
     */
    public static Gatherer<BlogPost, ?, Map.Entry<String, List<BlogPost>>> recentPostsByCategory(int limit) {
        return groupByWithLimit(
                BlogPost::category,
                limit,
                Comparator.comparing(BlogPost::publishedDate).reversed()
        );
    }
}