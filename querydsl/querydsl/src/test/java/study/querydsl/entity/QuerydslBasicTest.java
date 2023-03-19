package study.querydsl.entity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);


        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);


        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }


    @Test
    public void startJPQL() {
        //member1을 찾아라.
        Member result = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(result.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
        Member result = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        assertThat(result.getUsername()).isEqualTo("member1");
    }


    @Test
    public void search() {
        Member result = queryFactory
                .selectFrom(member)
                // username이 member1이면서, age가 10인사람 조회
                .where(member.username.eq("member1").and(member.age.eq(10)))
                .fetchOne();

        assertThat(result.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        Member result = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        (member.age.eq(10))
                )
                .fetchOne();

        assertThat(result.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch() {
        // 리스트 조회
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        // 단 건 조회
        Member memberOne = queryFactory
                .selectFrom(member)
                .fetchOne();


        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchFirst();// .limit(1).fetchOne() 동일한 반환

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .limit(1).fetchOne();

        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();
        results.getTotal();
        // 값을 꺼내야 content 타입이 반환된다.
        List<Member> content = results.getResults();


        long total = queryFactory
                .selectFrom(member)
                .fetchCount();

    }

    @Test
    public void sort() {
        /**
         * 회원 정렬 순서
         * 1. 회원 나이 내림차순 (desc)
         * 2. 회원 이름 오름차순 (asc)
         * 단 2에서 회원 이름이 없으면 마지막에 출력 (nulls last)
         */

        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));


        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                // username이 null 이면 마지막에 출력
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                // 몇 번째 앞에서 잘라서 사용, 0부터 시작
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        // 4인 이유는 위 로직 @beforeEach 어노테이션을 통해 데이터를 주입해줬음
        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getLimit()).isEqualTo(2);
        assertThat(result.getOffset()).isEqualTo(1);
        assertThat(result.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        // Tuple을 조회한다.
        List<Tuple> result = queryFactory
                .select(member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        // 위에있는 @beforeEach
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);


    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구하라
     */

    @Test
    public void group() {
        // Tuple을 조회한다.
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        // team name으로 그룹핑 하여 get(0)은 team A
        Tuple teamA = result.get(0);
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20) / 2;

        // teamB
        Tuple teamB = result.get(1);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40) / 2;

    }

    /**
     * 팀 A에 소속된 모든 멤버
     */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                // team = Qteam.team
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                // .extracting = 필요한 데이터만 추출하여 테스트
                .extracting("username")
                // .containsExactly = list의 값과 values의 순서대로 list에 있는지 테스트
                .containsExactly("member1", "member2");
    }


    /**
     * 세타 조인 (연관관계가 되어있지않아도 조인가능)
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join() {
        // 멤버 이름이 teamA, 팀 이름도 teamA
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }


    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조회, 회원은 모두 조회 (outer 조인)
     * JPQL : "select m, t from Member m left join m.team t on t.name = 'teamA'"
     */
    @Test
    public void join_on_filtering() {
        // Tuple로 반환되는 이유는 select문에서 값이 2개 이상임으로
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                // member 테이블은 다 가져오고 (left join), team 테이블은 teamA만 걸러서 가져온다.
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);

        }

    }


    /**
     * 연관관계가 없는 엔티티 외부조인
     * 회원의 이름이 팀 이름과 같은 대상을 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        // 멤버 이름이 teamA, 팀 이름도 teamA
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        // member 엔티티만 집는 쿼리
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // team이 이미 로딩된 엔티티인지 확인
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("패치 조인 미적용").isFalse();
    }


    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        // member를 조회할때 연관된 team을 한방 쿼리로 조인 한다.
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // team이 이미 로딩된 엔티티인지 확인
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("패치 조인 미적용").isTrue();
    }


    /**
     * 나이가 가장 많은 회원을 조회
     */

    @Test
    public void subQuery() {

        // 외부의 alias와 겹치면 안된다. (서브쿼리 사용하기때문)
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원을 조회
     */

    @Test
    public void subQueryGoe() {

        // 외부의 alias와 겹치면 안된다. (서브쿼리 사용하기때문)
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }


    @Test
    public void subQueryIn() {

        // 외부의 alias와 겹치면 안된다. (서브쿼리 사용하기때문)
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }


    }

    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열 살")
                        .when(20).then("스무 살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20))
                        .then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                // Constant(상수) A값을 쿼리에 넣어서 가져온다.
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {

            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() {
        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }

    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);

            System.out.println("username = " + username);
            System.out.println("age = " + age);


        }
    }

    @Test
    public void findDtoByJPQL() {
        // from 절과 MemberDto의 타입이 맞지않아 new 오퍼레이션 생성자로 만들어줘야된다.
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) " +
                        "from Member m ", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    // setter 접근 (프로퍼티 접근) get, set을 통해 접근하는방법임
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    // 필드 접근 (DTO의 필드값을 바로 집는다)
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    // 생성자 접근 방법 (타입이 맞아야 생성자를 생성해준다)
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    @Test
    public void findDtoByQueryProjection() {
        // DTO 클래스의 필드에 맞게 생성자가 Q 타입을 생성되어 파라미터로 값을 더 추가하면 컴파일 오류가 발생한다.
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }


    // 동적 쿼리 = 조회 조건이 동적으로 바뀌는 상황 
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
        // 값을 조회할때 동적으로 조회 조건이 바뀔수 있다.
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }


    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                // where 문에 null이 바인딩 되면 무시된다.
                .where(usernameEq(usernameCond), ageEq(ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        if (usernameCond == null) {
            return null;
        } else {
            return member.username.eq(usernameCond);
        }
    }

    private BooleanExpression ageEq(Integer ageCond) {
        if (ageCond == null) {
            return null;
        } else {
            return member.age.eq((ageCond));
        }
    }

    @Test
    public void bulkUpdate() {
        // 실행 전
        /*
         * member 1 = 10 --> DB member 1
         * member 2 = 20 --> DB member 2
         * member 3 = 30 --> DB member 3
         * member 4 = 40 --> DB member 4
         * */
        long count = queryFactory
                .update(member)
                // where 조건문에 따라 바인딩
                .set(member.username, "비회원")
                // member의 나이가 28살 이하인
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();
        // 실행 후
        /*
         * member 1 = 10 --> DB 비회원
         * member 2 = 20 --> DB 비회원
         * member 3 = 30 --> DB member 3
         * member 4 = 40 --> DB member 4
         * */
    }

    @Test
    public void bulkAdd() {
        long result = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
//                .set(member.age, member.age.multiply(2));
                .execute();

    }

    @Test
    public void bulkDelete() {
        long result = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);

        }

    }


}
