class State:
    verbose = False


class BColors:
    """ Catalog of colors that can be used in the console. """
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


class Out:
    @staticmethod
    def debug(message, flag=True):
        if flag:
            print(f"DEBUG: {message}")

    @staticmethod
    def info(message):
        print(f"{BColors.OKBLUE}INFO{BColors.ENDC}: {message}")

    @staticmethod
    def ok(message):
        print(f"{BColors.OKGREEN}{BColors.BOLD}INFO{BColors.ENDC}: {message}")

    @staticmethod
    def warn(message, *args):
        print(f"{BColors.WARNING}{BColors.BOLD}WARN{BColors.ENDC}: {message}", *args)

    @staticmethod
    def error(message, *args):
        print(f"{BColors.FAIL}{BColors.BOLD}ERROR{BColors.ENDC}: {message}", *args)

    @staticmethod
    def usage(command, help_message):
        print(f"""
        {BColors.HEADER}Command: {BColors.ENDC}{command}
        {help_message}
        """)
