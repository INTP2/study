package beinet.cn.demounittestmockito;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.Assert;

import java.util.regex.Pattern;

// 官方说明 https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
@SpringBootTest
@ActiveProfiles("unittest")
class UseMockBean {

    // 表示要对 businessService 这个bean的方法进行mock处理
    @MockBean
    private BusinessService businessService;

    /**
     * 使用注解定义的mock对象
     */
    @Test
    void testMockBeanMethod() {
        DbController controller = new DbController(businessService);
        testBeanMethod(businessService, controller);
    }

    /**
     * 方法里直接生成mock代理对象
     */
    @Test
    void testMockDirectMethod() {
        BusinessService service = Mockito.mock(BusinessService.class);
        DbController controller = new DbController(service);
        testBeanMethod(service, controller);
    }

    static void testBeanMethod(BusinessService businessService, DbController controller) {
        // 对 requestBaiduHtml 方法进行mock
        Mockito.when(businessService.requestBaiduHtml()).thenReturn("我是Mock后的百度");

        // 测试 调用mock方法
        String ret = controller.getBaidu();
        Assert.isTrue(ret.equals("我是Mock后的百度"), "mock失败？");

        // 测试 调用没有mock的方法，会直接返回null
        ret = controller.getSina();
        Assert.isTrue(ret == null, "啥情况？");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Mock指定调用真实方法 用法1
        Mockito.when(businessService.requestSinaHtml()).thenAnswer(Answers.CALLS_REAL_METHODS);
        // 测试 调用真实方法
        ret = controller.getSina();
        Assert.isTrue(ret.equals("我是新浪"), "啥情况？");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Mock指定调用真实方法 用法2
        Mockito.doCallRealMethod().when(businessService).requestByPara("");
        // 测试 调用真实方法
        ret = controller.getWithPara("");
        Assert.isTrue(ret.equals("我收到参数:isEMPTY"), "啥情况？");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // 对requestByPara，且参数为123进行mock
        Mockito.when(businessService.requestByPara("123")).thenReturn("mock参数123");
        // 测试 调用requestByPara("123")
        ret = controller.getWithPara("123");
        Assert.isTrue(ret.equals("mock参数123"), "啥情况？");
        // 测试 调用requestByPara 未mock的参数
        ret = controller.getWithPara("456");
        Assert.isTrue(ret == null, "啥情况？");

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // 对requestByPara，任意字符串参数 进行mock
        Mockito.when(businessService.requestByPara(ArgumentMatchers.anyString())).thenAnswer(invocationOnMock -> {
            Object arg = invocationOnMock.getArgument(0);
            if (arg == null || arg.toString().isEmpty()) {
                return "Mock收到空值";
            }

            Pattern pattern = Pattern.compile("^\\d+$");
            if (pattern.matcher(arg.toString()).find()) {
                return "Mock掉数值调用:" + arg;
            }
            // 不是空，也不是数值，直接调用源方法
            return invocationOnMock.callRealMethod();
        });

        // 测试 null参数的mock, anyString()不支持null参数
        ret = controller.getWithPara(null);
        Assert.isTrue(ret == null, "啥情况？");
        // 测试 empty参数的mock
        ret = controller.getWithPara("");
        Assert.isTrue(ret.equals("Mock收到空值"), "啥情况？");
        // 测试 Answer逻辑里的数值参数
        ret = controller.getWithPara("123");
        Assert.isTrue(ret.equals("Mock掉数值调用:123"), "啥情况？");
        // 测试 Answer逻辑里的非数值参数
        ret = controller.getWithPara("123a");
        Assert.isTrue(ret.equals("我收到参数:123a"), "啥情况？");


        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // 直接抛出原有方法的异常
        Mockito.when(businessService.throwExpWithRet1(ArgumentMatchers.any())).thenCallRealMethod();
        // 测试断言1：执行 throwExpWithRet1方法 必须返回IllegalArgumentException异常
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> businessService.throwExpWithRet1(""));
        // 测试断言2：执行 throwExpWithRet1方法 必须返回IllegalArgumentException异常, 并断言异常Message
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> businessService.throwExpWithRet1(""))
                .withMessage("抛个异常2")
                .withNoCause(); // 该异常就是最终异常，没有内部异常

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // mock返回自定义异常
        IllegalArgumentException iaExp = new IllegalArgumentException("非法参数异常");
        Mockito.when(businessService.throwExpWithRet2(ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("抛运行时异常", iaExp));
        // 测试断言1：执行 throwExpWithRet2方法 必须返回mock后的 RuntimeException 异常
        org.junit.Assert.assertThrows(RuntimeException.class, () -> businessService.throwExpWithRet2(""));
        // 测试断言2：执行 throwExpWithRet2方法 必须返回mock后的 RuntimeException 异常, 并断言异常Message
        Assertions.assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> businessService.throwExpWithRet2(""))
                .withMessage("抛运行时异常")
                .withCause(iaExp); // 该异常是由 iaExp 引发的

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // 添加void方法的mock测试
        // 下面这么写会抛异常：此处不允许使用'空'类型，即void
        // Mockito.when(businessService.noReturnMethod(ArgumentMatchers.any(), ArgumentMatchers.any()));
        ArgumentCaptor<Object> arg1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Long> arg2 = ArgumentCaptor.forClass(Long.class);
        // doNothing忽略方法调用，并把方法的2个参数进行捕获
        Mockito.doNothing().when(businessService).noReturnMethod1(arg1.capture(), arg2.capture());
        // 方法调用
        String realArg1 = "我是参数1";
        long realArg2 = 123567;
        businessService.noReturnMethod1(realArg1, realArg2);
        // 对捕获的参数进行断言
        Assert.isTrue(realArg1.equals(arg1.getValue()), "");
        Assert.isTrue(realArg2 == arg2.getValue(), "");

        // void方法测试2，替换void方法
        Mockito.doAnswer(invocation -> {
            Object objArg = invocation.getArgument(1);
            Long longArg = invocation.getArgument(0);
            System.out.println(objArg + "===" + longArg);

            // 对捕获的参数进行断言
            Assert.isTrue(realArg1.equals(objArg), "");
            Assert.isTrue(realArg2 == longArg, "");

            return invocation.callRealMethod();// 需要时，这里可以回调原始方法
        }).when(businessService).noReturnMethod2(ArgumentMatchers.anyLong(), ArgumentMatchers.any());
        businessService.noReturnMethod2(realArg2, realArg1);
    }


}
