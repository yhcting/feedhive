package free.yhc.feeder;

class PredefinedValues {
    static class Channel {
        String   name;
        String   url;
        String   imageref;
        Channel(String name, String url, String imageref) {
            this.name = name;
            this.url = url;
            this.imageref = imageref;
        }
    }

    static class Category {
        String       name;
        Channel[]    channels;

        Category(String name, Channel[] channels) {
            this.name = name;
            this.channels = channels;
        }
    }

    private static class CategoryChannelMap {
        String      categoryName;
        String[]    channelInfo;
        CategoryChannelMap(String[] channelInfo, String categoryName) {
            this.channelInfo = channelInfo;
            this.categoryName = categoryName;
        }
    }

    private static final int    indexUrl       = 0;
    private static final int    indexImageref  = 1;
    private static final int    indexName      = 2;
    private static final int    nrIndex        = 3;

    private static final String[] presses = new String[] {
        "http://rss.ohmynews.com/rss/ohmynews.xml",             "",                                                 "오마이뉴스 ",
        "http://rss.nocutnews.co.kr/nocutnews.xml",             "",                                                 "노컷뉴스 ",
        "http://www.hani.co.kr/rss/",                           "",                                                 "한겨레 ",
        "http://www.khan.co.kr/rss/rssdata/total_news.xml",     "",                                                 "경향신문",
        "http://file.mk.co.kr/news/rss/rss_30000001.xml",       "",                                                 "매일경제 ",
        "http://rss.hankooki.com/news/hk_main.xml",             "",                                                 "한국일보",
        "http://rss.etnews.com/Section901.xml",                 "",                                                 "전자신문",
        "http://www.sportsseoul.com/rss/rss.asp?cp_flag=1",     "",                                                 "스포츠서울",
        "http://www.inews24.com/rss/news_inews.xml",            "",                                                 "아이뉴스",
        "http://api.sbs.co.kr/xml/news/rss.jsp?pmDiv=all",      "",                                                 "SBS",
        "http://imnews.imbc.com/rss/news/news_00.xml",          "",                                                 "MBC",
    };

    private static final String[] potcasts = new String[] {
        "http://old.ddanzi.com/appstream/ddradio.xml",          "http://cfile24.uf.tistory.com/image/113B324D4F3A76A80BE6E1", "나는 꼼수다",
        "http://old.ddanzi.com/appstream/ggobsari.xml",         "http://cfile28.uf.tistory.com/image/123B324D4F3A76A809124F", "나는 꼽사리다",
        "http://http://cast.vop.co.kr/kfline.xml",              "",                                                 "애국전선",
        "http://www.viddler.com/rss/newstapa",                  "",                                                 "뉴스타파",
        "http://test.handypia.org/radio/jugong.xml",            "",                                                 "저공비행",
        "http://rss.ohmynews.com/RSS/podcast_etul_main.xml",    "",                                                 "이털남",
        "http://cast.vop.co.kr/heenews.xml",                    "",                                                 "희뉴스",
        "http://cast.vop.co.kr/nang.xml",                       "",                                                 "낭만자객",
        "http://rss.ohmynews.com/rss/podcast_bbong_main.xml",   "",                                                 "이해찬의 정석정치",
        "http://www.615tv.net/podcastbanmin/jkbsbanmin.xml",    "",                                                 "라디오 반민특위",
        "http://cbspodcast.com/podcast/sisa/sisa.xml",          "",                                                 "시사자키 정관용입니다",
        "http://cbspodcast.com/podcast/newsshow_journal/newsshow_journal.xml",  "",                                 "변상욱의 기자수첩",
        "http://cbspodcast.com/podcast/k_everyone/k_everyone.xml", "",                                              "김미화의 여러분",
        "http://nemo.podics.com/131613387490",                  "",                                                 "정혜림의 도도한 뒷담화",
        "http://cbspodcast.com/podcast/newshow/newshow.xml",    "",                                                 "김현정의 뉴스쇼",
        "http://rss.ohmynews.com/rss/podcast_authortalk_main.xml", "",                                              "오마이tv 저자 대화",
        "http://www.docdocdoc.co.kr/podcast/iam_doctors.xml",   "",                                                 "나는 의사다",
    };

    private static final String[] etcs = new String[] {
        "http://podcast.kseri.net/forum/pod_audio.xml",         "",                                                 "김광수경제연구소 포럼 공부방",
        "http://www.artnstudy.com/podcast.xml",                 "",                                                 "아트앤스터디 인문강의 - 수유 너머",
        "http://nemo.podics.com/131113737488",                  "",                                                 "그들이 말하지 않는 23가지 - 장하준",
        "http://nemo.podics.com/132607634128",                  "",                                                 "문국현과 새로운 세상",
        "http://nemo.podics.com/131192848437",                  "",                                                 "망치부인 시사수다방",
        "http://rss.hankooki.com/podcast/",                     "",                                                 "한국일보 시사난타H",
        "http://poisontongue.sisain.co.kr/rss",                 "",                                                 "고재열의 독설닷컴",
        "http://mobizen.pe.kr/rss",                             "",                                                 "모바일 컨텐츠 이야기",
        "http://www.leejeonghwan.com/media/atom.xml",           "",                                                 "이정환 닷컴",
        "http://www.linxus.co.kr/lib/rssblog.asp?blogid=yehbyungil", "",                                            "예병일의 경제노트",
    };

    private static final CategoryChannelMap[] ccmap = new CategoryChannelMap[] {
        new CategoryChannelMap(presses,     "언론"),
        new CategoryChannelMap(potcasts,    "팟캐스트"),
        new CategoryChannelMap(etcs,        "기타"),
    };

    static Category[]
    getPredefinedChannels() {
        Category[] cats = new Category[ccmap.length];
        for (int i = 0; i < ccmap.length; i++) {
            cats[i] = new Category(ccmap[i].categoryName,
                                   new Channel[ccmap[i].channelInfo.length / nrIndex]);
            for (int j = 0; j < ccmap[i].channelInfo.length / nrIndex; j++) {
                String[] s = ccmap[i].channelInfo;
                int      base = j * nrIndex;
                cats[i].channels[j] = new Channel(s[base + indexName], s[base + indexUrl], s[base + indexImageref]);
            }
        }
        return cats;
    }

}
