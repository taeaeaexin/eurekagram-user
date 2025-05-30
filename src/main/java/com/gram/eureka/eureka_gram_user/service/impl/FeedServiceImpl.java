package com.gram.eureka.eureka_gram_user.service.impl;

import com.gram.eureka.eureka_gram_user.dto.FeedRequestDto;
import com.gram.eureka.eureka_gram_user.dto.FeedResponseDto;
import com.gram.eureka.eureka_gram_user.dto.MyFeedDto;
import com.gram.eureka.eureka_gram_user.dto.MyFeedsResponseDto;
import com.gram.eureka.eureka_gram_user.dto.query.FeedDto;
import com.gram.eureka.eureka_gram_user.entity.Feed;
import com.gram.eureka.eureka_gram_user.entity.FeedView;
import com.gram.eureka.eureka_gram_user.entity.Image;
import com.gram.eureka.eureka_gram_user.entity.User;
import com.gram.eureka.eureka_gram_user.entity.enums.Status;
import com.gram.eureka.eureka_gram_user.repository.*;
import com.gram.eureka.eureka_gram_user.service.FeedService;
import com.gram.eureka.eureka_gram_user.util.ImageUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FeedServiceImpl implements FeedService {
    private final FeedRepository feedRepository;
    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final ImageUtil imageUtil;
    private final CommentRepository commentRepository;
    private final FeedViewRepository feedViewRepository;

    @Override
    public FeedResponseDto createFeed(FeedRequestDto feedRequestDto) {
        // User 엔티티 생성 > Jwt 토큰으로부터 정보 추출 및 findByEmail 실행
        String email = SecurityContextHolder.getContext().getAuthentication().getName(); // 기본적으로 username 반환
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new UsernameNotFoundException("User not found")
        );

        // 피드 등록을 위한 feed 엔티티 생성
        Feed feed = Feed.builder()
                .content(feedRequestDto.getContent())
                .user(user)
                .status(Status.ACTIVE)
                .build();
        // feed 등록
        feedRepository.save(feed);

        // feed 매핑되는 이미지 등록
        List<Image> imageList = new ArrayList<>();
        for (MultipartFile mpFile : feedRequestDto.getImages()) {
            String fullName = mpFile.getOriginalFilename();
            String[] splitFullName = fullName.split("\\.");
            String extension = splitFullName[splitFullName.length - 1];

            Image image = Image.builder()
                    .feed(feed)
                    .originalImageName(fullName)
                    .storedImageName(imageUtil.saveImageToDisk(mpFile))
                    .imageExtension(extension)
                    .build();

            imageList.add(image);
        }

        imageRepository.saveAll(imageList);

        FeedResponseDto feedResponseDto = new FeedResponseDto();
        feedResponseDto.setFeedId(feed.getId());
        return feedResponseDto;
    }

    @Override
    public FeedDto detailFeed(Long feedId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName(); // 기본적으로 username 반환
        User findUser = userRepository.findByEmail(email).orElseThrow(
                () -> new UsernameNotFoundException("User not found")
        );

        Long userId = findUser.getId();
        if (feedViewRepository.findExistByFeedIdAndUserId(feedId, userId)) {
            FeedView feedView = FeedView.builder()
                    .user(findUser)
                    .feed(feedRepository.findById(feedId).get())
                    .build();

            feedViewRepository.save(feedView);
        }

        FeedDto feedDto = feedRepository.findFeedInfoById(feedId, userId);
        Long feedViewCount = feedViewRepository.getFeedViewCount(feedId);
        feedDto.setFeedViewCount(feedViewCount);
        return feedDto;
    }

    @Override
    public FeedResponseDto updateFeed(Long id) {
        Long updateFeedCount = feedRepository.updateFeedStatusById(id);
        FeedResponseDto feedResponseDto = new FeedResponseDto();
        feedResponseDto.setFeedCount(updateFeedCount);
        return feedResponseDto;
    }

    public MyFeedsResponseDto myFeed() {
        // User 엔티티 생성 > Jwt 토큰으로부터 정보 추출 및 findByEmail 실행
        String email = SecurityContextHolder.getContext().getAuthentication().getName(); // 기본적으로 username 반환
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new UsernameNotFoundException("User not found")
        );

        // 내 피드 목록 조회 (feed_id, img name 리스트)
        List<MyFeedDto> feeds = feedRepository.findFeedByUser(user);

        // 내 피드 개수
        int count = feeds.size();

        return MyFeedsResponseDto.builder()
                .feeds(feeds)
                .count(count)
                .build();
    }

    @Override
    public List<FeedResponseDto> getFeeds(Long lastFeedId, int size, String nickname) {
        List<Feed> feeds;

        if (!StringUtils.hasText(nickname)) {
            feeds = feedRepository.findFeeds(lastFeedId, PageRequest.of(0, size));
        } else {
            feeds = feedRepository.findFeedsByNickname(nickname, lastFeedId, PageRequest.of(0, size));
        }

        if (feeds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> feedIds = feeds.stream()
                .map(Feed::getId)
                .toList(); // Java 16+ or use .collect(Collectors.toList())

        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByFeedId(feedIds));
        Map<Long, Long> viewCounts = toCountMap(feedViewRepository.countByFeedId(feedIds));

        return feeds.stream().map(feed -> {
            FeedResponseDto dto = new FeedResponseDto();
            dto.setNickName(feed.getUser().getNickName());
            dto.setCreateDate(feed.getCreatedAt());
            dto.setFeedId(feed.getId());
            dto.setContent(feed.getContent());
            dto.setImages(
                    feed.getImages().stream()
                            .map(image -> "/images/" + image.getStoredImageName())
                            .toList()
            );
            dto.setCommentCount(commentCounts.getOrDefault(feed.getId(), 0L));
            dto.setViewCount(viewCounts.getOrDefault(feed.getId(), 0L));
            return dto;
        }).toList();
    }

    @Override
    public FeedResponseDto updateFeed(FeedRequestDto feedRequestDto) {
        Feed feed = feedRepository.findById(feedRequestDto.getId()).orElseThrow(
                () -> new EntityNotFoundException("Feed not found")
        );

        feed.setContent(feedRequestDto.getContent());

        List<Image> originalImages = feed.getImages();
        if (originalImages == null) {
            originalImages = new ArrayList<>();
        }

        // 기존 피드의 이미지 아이디
        List<Long> originalImageIds = originalImages.stream()
                .map(Image::getId)
                .collect(Collectors.toList());

        // 프론트에서 전달된 이미지 아이디
        List<Long> remainImageIds = feedRequestDto.getRemainImageIds();
        if (remainImageIds == null) {
            remainImageIds = new ArrayList<>();
        }

        // 삭제해야 하는 이미지 아이디 = 기존 피드 이미지 아이디 - 프론트에서 전달된 이미지 아이디
        originalImageIds.removeAll(remainImageIds);

        if (!originalImageIds.isEmpty()) {
            imageRepository.updateStatusByIds(originalImageIds);
        }

        // feed 매핑되는 이미지 등록
        List<MultipartFile> images = feedRequestDto.getImages();
        if (images != null) {
            List<Image> imageList = new ArrayList<>();
            for (MultipartFile mpFile : images) {
                String fullName = mpFile.getOriginalFilename();
                String[] splitFullName = fullName.split("\\.");
                String extension = splitFullName[splitFullName.length - 1];

                Image image = Image.builder()
                        .feed(feed)
                        .originalImageName(fullName)
                        .storedImageName(imageUtil.saveImageToDisk(mpFile))
                        .imageExtension(extension)
                        .build();

                imageList.add(image);
            }
            imageRepository.saveAll(imageList);
        }

        FeedResponseDto feedResponseDto = new FeedResponseDto();
        feedResponseDto.setFeedId(feed.getId());
        return feedResponseDto;
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            Long feedId = ((Number) row[0]).longValue();
            Long count = ((Number) row[1]).longValue();
            map.put(feedId, count);
        }
        return map;
    }
}
